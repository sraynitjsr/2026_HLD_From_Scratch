package main

import (
	"context"
	"encoding/json"
	"fmt"
	"hash/fnv"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

// ============================================================================
// Configuration
// ============================================================================

type Config struct {
	Port            string
	JWTSecret       string
	RateLimitWindow time.Duration
	RateLimitMax    int
}

type ServiceConfig struct {
	Name      string
	Backends  []string
	HealthURL string
	RateLimit int
}

// ============================================================================
// API Gateway Core
// ============================================================================

type APIGateway struct {
	config        *Config
	router        *Router
	loadBalancer  *LoadBalancer
	authenticator *Authenticator
	rateLimiter   *RateLimiter
	middleware    []Middleware
	healthChecker *HealthChecker
}

func NewAPIGateway(config *Config) *APIGateway {
	return &APIGateway{
		config:        config,
		router:        NewRouter(),
		loadBalancer:  NewLoadBalancer(),
		authenticator: NewAuthenticator(config.JWTSecret),
		rateLimiter:   NewRateLimiter(config.RateLimitWindow, config.RateLimitMax),
		healthChecker: NewHealthChecker(),
		middleware:    []Middleware{},
	}
}

func (gw *APIGateway) RegisterService(path string, config ServiceConfig) {
	// Register backends with load balancer
	backends := make([]*Backend, len(config.Backends))
	for i, addr := range config.Backends {
		parsedURL, err := url.Parse(addr)
		if err != nil {
			log.Printf("Error parsing backend URL %s: %v", addr, err)
			continue
		}
		backends[i] = NewBackend(parsedURL, config.Name)
	}
	gw.loadBalancer.AddService(config.Name, backends)

	// Register route
	gw.router.AddRoute(path, config.Name)

	// Start health checks if configured
	if config.HealthURL != "" {
		for _, backend := range backends {
			gw.healthChecker.Monitor(backend, config.HealthURL, 10*time.Second)
		}
	}
}

func (gw *APIGateway) Use(middleware Middleware) {
	gw.middleware = append(gw.middleware, middleware)
}

func (gw *APIGateway) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// Generate correlation ID
	correlationID := uuid.New().String()
	r.Header.Set("X-Correlation-ID", correlationID)

	startTime := time.Now()

	// Create response writer wrapper for metrics
	rw := &responseWriter{ResponseWriter: w, statusCode: http.StatusOK}

	// Build middleware chain
	handler := http.HandlerFunc(gw.handleRequest)
	for i := len(gw.middleware) - 1; i >= 0; i-- {
		handler = gw.middleware[i](handler)
	}

	// Execute
	handler.ServeHTTP(rw, r)

	// Log request
	duration := time.Since(startTime)
	log.Printf("[%s] %s %s - Status: %d - Duration: %v - ID: %s",
		r.Method, r.URL.Path, r.RemoteAddr, rw.statusCode, duration, correlationID)
}

func (gw *APIGateway) handleRequest(w http.ResponseWriter, r *http.Request) {
	// Match route
	serviceName := gw.router.Match(r.URL.Path)
	if serviceName == "" {
		http.Error(w, "Service not found", http.StatusNotFound)
		return
	}

	// Get backend from load balancer
	backend := gw.loadBalancer.GetBackend(serviceName)
	if backend == nil {
		http.Error(w, "No healthy backend available", http.StatusServiceUnavailable)
		return
	}

	// Create reverse proxy
	proxy := httputil.NewSingleHostReverseProxy(backend.URL)
	proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		log.Printf("Proxy error: %v", err)
		backend.MarkUnhealthy()
		http.Error(w, "Bad Gateway", http.StatusBadGateway)
	}

	// Modify request
	r.URL.Host = backend.URL.Host
	r.URL.Scheme = backend.URL.Scheme
	r.Header.Set("X-Forwarded-Host", r.Header.Get("Host"))
	r.Host = backend.URL.Host

	// Forward request
	proxy.ServeHTTP(w, r)
}

func (gw *APIGateway) Start() error {
	log.Printf("Starting API Gateway on port %s", gw.config.Port)
	return http.ListenAndServe(":"+gw.config.Port, gw)
}

// ============================================================================
// Router
// ============================================================================

type Router struct {
	routes map[string]string // path prefix -> service name
	mu     sync.RWMutex
}

func NewRouter() *Router {
	return &Router{
		routes: make(map[string]string),
	}
}

func (r *Router) AddRoute(pathPrefix, serviceName string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.routes[pathPrefix] = serviceName
	log.Printf("Registered route: %s -> %s", pathPrefix, serviceName)
}

func (r *Router) Match(path string) string {
	r.mu.RLock()
	defer r.mu.RUnlock()

	// Find longest matching prefix
	longestMatch := ""
	serviceName := ""

	for prefix, service := range r.routes {
		if strings.HasPrefix(path, prefix) && len(prefix) > len(longestMatch) {
			longestMatch = prefix
			serviceName = service
		}
	}

	return serviceName
}

// ============================================================================
// Load Balancer
// ============================================================================

type Backend struct {
	URL      *url.URL
	Name     string
	Healthy  bool
	mu       sync.RWMutex
	requests int64
}

func NewBackend(url *url.URL, name string) *Backend {
	return &Backend{
		URL:     url,
		Name:    name,
		Healthy: true,
	}
}

func (b *Backend) IsHealthy() bool {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.Healthy
}

func (b *Backend) MarkHealthy() {
	b.mu.Lock()
	defer b.mu.Unlock()
	if !b.Healthy {
		log.Printf("Backend %s is now healthy", b.URL.String())
	}
	b.Healthy = true
}

func (b *Backend) MarkUnhealthy() {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.Healthy {
		log.Printf("Backend %s is now unhealthy", b.URL.String())
	}
	b.Healthy = false
}

type LoadBalancer struct {
	services map[string][]*Backend
	counter  map[string]int
	mu       sync.RWMutex
}

func NewLoadBalancer() *LoadBalancer {
	return &LoadBalancer{
		services: make(map[string][]*Backend),
		counter:  make(map[string]int),
	}
}

func (lb *LoadBalancer) AddService(name string, backends []*Backend) {
	lb.mu.Lock()
	defer lb.mu.Unlock()
	lb.services[name] = backends
	lb.counter[name] = 0
	log.Printf("Added service %s with %d backends", name, len(backends))
}

// GetBackend uses Round Robin algorithm
func (lb *LoadBalancer) GetBackend(serviceName string) *Backend {
	lb.mu.Lock()
	defer lb.mu.Unlock()

	backends, exists := lb.services[serviceName]
	if !exists || len(backends) == 0 {
		return nil
	}

	// Try to find healthy backend (max attempts = number of backends)
	attempts := 0
	for attempts < len(backends) {
		idx := lb.counter[serviceName] % len(backends)
		lb.counter[serviceName]++

		backend := backends[idx]
		if backend.IsHealthy() {
			return backend
		}
		attempts++
	}

	return nil
}

// GetBackendConsistentHash uses consistent hashing for sticky sessions
func (lb *LoadBalancer) GetBackendConsistentHash(serviceName, key string) *Backend {
	lb.mu.RLock()
	defer lb.mu.RUnlock()

	backends, exists := lb.services[serviceName]
	if !exists || len(backends) == 0 {
		return nil
	}

	// Hash the key
	h := fnv.New32a()
	h.Write([]byte(key))
	hash := h.Sum32()

	// Find backend using consistent hashing
	idx := int(hash) % len(backends)

	// Try to find healthy backend
	for i := 0; i < len(backends); i++ {
		backend := backends[(idx+i)%len(backends)]
		if backend.IsHealthy() {
			return backend
		}
	}

	return nil
}

// ============================================================================
// Health Checker
// ============================================================================

type HealthChecker struct {
	monitors map[*Backend]context.CancelFunc
	mu       sync.Mutex
}

func NewHealthChecker() *HealthChecker {
	return &HealthChecker{
		monitors: make(map[*Backend]context.CancelFunc),
	}
}

func (hc *HealthChecker) Monitor(backend *Backend, healthURL string, interval time.Duration) {
	hc.mu.Lock()
	defer hc.mu.Unlock()

	ctx, cancel := context.WithCancel(context.Background())
	hc.monitors[backend] = cancel

	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				hc.checkHealth(backend, healthURL)
			}
		}
	}()
}

func (hc *HealthChecker) checkHealth(backend *Backend, healthPath string) {
	healthURL := backend.URL.String() + healthPath

	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(healthURL)

	if err != nil || resp.StatusCode != http.StatusOK {
		backend.MarkUnhealthy()
		return
	}
	defer resp.Body.Close()

	backend.MarkHealthy()
}

func (hc *HealthChecker) Stop(backend *Backend) {
	hc.mu.Lock()
	defer hc.mu.Unlock()

	if cancel, exists := hc.monitors[backend]; exists {
		cancel()
		delete(hc.monitors, backend)
	}
}

// ============================================================================
// Authentication
// ============================================================================

type Authenticator struct {
	jwtSecret []byte
}

func NewAuthenticator(secret string) *Authenticator {
	return &Authenticator{
		jwtSecret: []byte(secret),
	}
}

type Claims struct {
	UserID string   `json:"user_id"`
	Role   string   `json:"role"`
	Scopes []string `json:"scopes"`
	jwt.RegisteredClaims
}

func (a *Authenticator) ValidateJWT(tokenString string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenString, &Claims{}, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return a.jwtSecret, nil
	})

	if err != nil {
		return nil, err
	}

	if claims, ok := token.Claims.(*Claims); ok && token.Valid {
		return claims, nil
	}

	return nil, fmt.Errorf("invalid token")
}

func (a *Authenticator) GenerateJWT(userID, role string, scopes []string, duration time.Duration) (string, error) {
	claims := &Claims{
		UserID: userID,
		Role:   role,
		Scopes: scopes,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(duration)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(a.jwtSecret)
}

// ============================================================================
// Rate Limiter (Token Bucket Algorithm)
// ============================================================================

type RateLimiter struct {
	buckets map[string]*TokenBucket
	window  time.Duration
	maxRate int
	mu      sync.RWMutex
}

type TokenBucket struct {
	tokens         float64
	lastRefillTime time.Time
	capacity       float64
	refillRate     float64
	mu             sync.Mutex
}

func NewRateLimiter(window time.Duration, maxRate int) *RateLimiter {
	return &RateLimiter{
		buckets: make(map[string]*TokenBucket),
		window:  window,
		maxRate: maxRate,
	}
}

func (rl *RateLimiter) Allow(identifier string) bool {
	rl.mu.Lock()
	bucket, exists := rl.buckets[identifier]
	if !exists {
		bucket = &TokenBucket{
			tokens:         float64(rl.maxRate),
			lastRefillTime: time.Now(),
			capacity:       float64(rl.maxRate),
			refillRate:     float64(rl.maxRate) / rl.window.Seconds(),
		}
		rl.buckets[identifier] = bucket
	}
	rl.mu.Unlock()

	return bucket.consume(1)
}

func (tb *TokenBucket) consume(tokens float64) bool {
	tb.mu.Lock()
	defer tb.mu.Unlock()

	// Refill tokens
	now := time.Now()
	elapsed := now.Sub(tb.lastRefillTime).Seconds()
	tb.tokens = min(tb.capacity, tb.tokens+elapsed*tb.refillRate)
	tb.lastRefillTime = now

	// Try to consume
	if tb.tokens >= tokens {
		tb.tokens -= tokens
		return true
	}

	return false
}

func (rl *RateLimiter) GetRemaining(identifier string) int {
	rl.mu.RLock()
	bucket, exists := rl.buckets[identifier]
	rl.mu.RUnlock()

	if !exists {
		return rl.maxRate
	}

	bucket.mu.Lock()
	defer bucket.mu.Unlock()
	return int(bucket.tokens)
}

// ============================================================================
// Middleware
// ============================================================================

type Middleware func(http.Handler) http.Handler

func AuthMiddleware(auth *Authenticator) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			// Skip auth for health check endpoints
			if strings.HasSuffix(r.URL.Path, "/health") {
				next.ServeHTTP(w, r)
				return
			}

			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				http.Error(w, "Authorization header required", http.StatusUnauthorized)
				return
			}

			// Extract Bearer token
			parts := strings.Split(authHeader, " ")
			if len(parts) != 2 || parts[0] != "Bearer" {
				http.Error(w, "Invalid authorization header format", http.StatusUnauthorized)
				return
			}

			claims, err := auth.ValidateJWT(parts[1])
			if err != nil {
				http.Error(w, "Invalid token", http.StatusUnauthorized)
				return
			}

			// Add user context to request
			r.Header.Set("X-User-ID", claims.UserID)
			r.Header.Set("X-User-Role", claims.Role)

			next.ServeHTTP(w, r)
		})
	}
}

func RateLimitMiddleware(limiter *RateLimiter) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			// Use user ID if available, otherwise IP
			identifier := r.Header.Get("X-User-ID")
			if identifier == "" {
				identifier = r.RemoteAddr
			}

			if !limiter.Allow(identifier) {
				w.Header().Set("X-RateLimit-Limit", fmt.Sprintf("%d", limiter.maxRate))
				w.Header().Set("X-RateLimit-Remaining", "0")
				w.Header().Set("Retry-After", "60")

				http.Error(w, "Rate limit exceeded", http.StatusTooManyRequests)
				return
			}

			remaining := limiter.GetRemaining(identifier)
			w.Header().Set("X-RateLimit-Limit", fmt.Sprintf("%d", limiter.maxRate))
			w.Header().Set("X-RateLimit-Remaining", fmt.Sprintf("%d", remaining))

			next.ServeHTTP(w, r)
		})
	}
}

func CORSMiddleware() Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Access-Control-Allow-Origin", "*")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")

			if r.Method == "OPTIONS" {
				w.WriteHeader(http.StatusOK)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

// ============================================================================
// Utilities
// ============================================================================

type responseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (rw *responseWriter) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}

func min(a, b float64) float64 {
	if a < b {
		return a
	}
	return b
}

// ============================================================================
// Example Usage
// ============================================================================

func main() {
	// Configuration
	config := &Config{
		Port:            "8080",
		JWTSecret:       "your-secret-key",
		RateLimitWindow: time.Minute,
		RateLimitMax:    100,
	}

	// Create gateway
	gateway := NewAPIGateway(config)

	// Register middleware
	gateway.Use(CORSMiddleware())
	gateway.Use(RateLimitMiddleware(gateway.rateLimiter))
	gateway.Use(AuthMiddleware(gateway.authenticator))

	// Register services
	gateway.RegisterService("/api/users", ServiceConfig{
		Name:      "user-service",
		Backends:  []string{"http://localhost:3001", "http://localhost:3002"},
		HealthURL: "/health",
		RateLimit: 100,
	})

	gateway.RegisterService("/api/orders", ServiceConfig{
		Name:      "order-service",
		Backends:  []string{"http://localhost:4001", "http://localhost:4002"},
		HealthURL: "/health",
		RateLimit: 50,
	})

	gateway.RegisterService("/api/products", ServiceConfig{
		Name:      "product-service",
		Backends:  []string{"http://localhost:5001"},
		HealthURL: "/health",
		RateLimit: 200,
	})

	// Add health endpoint for the gateway itself
	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{
			"status": "healthy",
			"time":   time.Now().Format(time.RFC3339),
		})
	})

	// Start gateway
	log.Fatal(gateway.Start())
}
