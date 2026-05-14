package com.hld.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.*;
import java.util.stream.Collectors;

/**
 * Complete API Gateway Implementation in Java
 * 
 * Features:
 * - Request routing and load balancing
 * - JWT authentication and authorization
 * - Rate limiting (Token Bucket algorithm)
 * - Health checking
 * - Request/Response transformation
 * - Observability (logging, metrics)
 */
public class API_Gateway_Java {
    
    private static final Logger LOGGER = Logger.getLogger(API_Gateway_Java.class.getName());
    
    private final int port;
    private final Router router;
    private final LoadBalancer loadBalancer;
    private final Authenticator authenticator;
    private final RateLimiter rateLimiter;
    private final HealthChecker healthChecker;
    private final List<Middleware> middlewares;
    private HttpServer server;
    
    public API_Gateway_Java(Config config) {
        this.port = config.port;
        this.router = new Router();
        this.loadBalancer = new LoadBalancer();
        this.authenticator = new Authenticator(config.jwtSecret);
        this.rateLimiter = new RateLimiter(config.rateLimitMax, config.rateLimitWindowSeconds);
        this.healthChecker = new HealthChecker();
        this.middlewares = new ArrayList<>();
    }
    
    public void registerService(String pathPrefix, ServiceConfig config) {
        // Register backends with load balancer
        List<Backend> backends = config.backends.stream()
            .map(url -> new Backend(url, config.name))
            .collect(Collectors.toList());
        
        loadBalancer.addService(config.name, backends);
        
        // Register route
        router.addRoute(pathPrefix, config.name);
        
        // Start health checks
        if (config.healthURL != null) {
            backends.forEach(backend -> 
                healthChecker.monitor(backend, config.healthURL, 10)
            );
        }
        
        LOGGER.info(String.format("Registered service: %s -> %s with %d backends", 
            pathPrefix, config.name, backends.size()));
    }
    
    public void use(Middleware middleware) {
        middlewares.add(middleware);
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new GatewayHandler());
        server.setExecutor(Executors.newFixedThreadPool(50));
        server.start();
        
        LOGGER.info("API Gateway started on port " + port);
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            healthChecker.shutdown();
        }
    }
    
    private class GatewayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long startTime = System.currentTimeMillis();
            String correlationId = UUID.randomUUID().toString();
            exchange.getRequestHeaders().set("X-Correlation-ID", correlationId);
            
            try {
                // Execute middleware chain
                Context context = new Context(exchange);
                boolean proceed = executeMiddlewares(context);
                
                if (!proceed) {
                    return; // Middleware handled the response
                }
                
                // Route and forward request
                handleRequest(exchange);
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling request", e);
                sendError(exchange, 500, "Internal Server Error");
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                logRequest(exchange, duration, correlationId);
            }
        }
        
        private boolean executeMiddlewares(Context context) throws IOException {
            for (Middleware middleware : middlewares) {
                if (!middleware.handle(context)) {
                    return false;
                }
            }
            return true;
        }
        
        private void handleRequest(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // Match route
            String serviceName = router.match(path);
            if (serviceName == null) {
                sendError(exchange, 404, "Service not found");
                return;
            }
            
            // Get backend
            Backend backend = loadBalancer.getBackend(serviceName);
            if (backend == null) {
                sendError(exchange, 503, "No healthy backend available");
                return;
            }
            
            // Forward request
            forwardRequest(exchange, backend);
        }
        
        private void forwardRequest(HttpExchange exchange, Backend backend) throws IOException {
            try {
                // Build target URL
                String targetURL = backend.url + exchange.getRequestURI().toString();
                URL url = new URL(targetURL);
                
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(exchange.getRequestMethod());
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(30000);
                
                // Copy headers
                exchange.getRequestHeaders().forEach((key, values) -> {
                    if (!key.equalsIgnoreCase("Host")) {
                        values.forEach(value -> connection.setRequestProperty(key, value));
                    }
                });
                
                connection.setRequestProperty("X-Forwarded-Host", 
                    exchange.getRequestHeaders().getFirst("Host"));
                connection.setRequestProperty("X-Forwarded-For", 
                    exchange.getRemoteAddress().getAddress().getHostAddress());
                
                // Send request body
                if ("POST".equals(exchange.getRequestMethod()) || 
                    "PUT".equals(exchange.getRequestMethod())) {
                    connection.setDoOutput(true);
                    try (OutputStream os = connection.getOutputStream()) {
                        exchange.getRequestBody().transferTo(os);
                    }
                }
                
                // Get response
                int statusCode = connection.getResponseCode();
                
                // Copy response headers
                connection.getHeaderFields().forEach((key, values) -> {
                    if (key != null) {
                        values.forEach(value -> 
                            exchange.getResponseHeaders().add(key, value)
                        );
                    }
                });
                
                // Send response
                InputStream responseStream = statusCode < 400 ? 
                    connection.getInputStream() : connection.getErrorStream();
                
                byte[] responseBytes = responseStream.readAllBytes();
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
                
                backend.markHealthy();
                
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error forwarding request to " + backend.url, e);
                backend.markUnhealthy();
                sendError(exchange, 502, "Bad Gateway");
            }
        }
        
        private void logRequest(HttpExchange exchange, long duration, String correlationId) {
            LOGGER.info(String.format("[%s] %s %s - Status: %d - Duration: %dms - ID: %s",
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRemoteAddress(),
                exchange.getResponseCode(),
                duration,
                correlationId
            ));
        }
    }
    
    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String response = String.format("{\"error\": \"%s\"}", message);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    // ============================================================================
    // Router
    // ============================================================================
    
    static class Router {
        private final Map<String, String> routes = new ConcurrentHashMap<>();
        
        public void addRoute(String pathPrefix, String serviceName) {
            routes.put(pathPrefix, serviceName);
        }
        
        public String match(String path) {
            // Find longest matching prefix
            return routes.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
        }
    }
    
    // ============================================================================
    // Load Balancer
    // ============================================================================
    
    static class LoadBalancer {
        private final Map<String, List<Backend>> services = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
        
        public void addService(String name, List<Backend> backends) {
            services.put(name, backends);
            counters.put(name, new AtomicInteger(0));
        }
        
        // Round Robin algorithm
        public Backend getBackend(String serviceName) {
            List<Backend> backends = services.get(serviceName);
            if (backends == null || backends.isEmpty()) {
                return null;
            }
            
            AtomicInteger counter = counters.get(serviceName);
            int attempts = 0;
            
            while (attempts < backends.size()) {
                int index = counter.getAndIncrement() % backends.size();
                Backend backend = backends.get(index);
                
                if (backend.isHealthy()) {
                    return backend;
                }
                attempts++;
            }
            
            return null;
        }
        
        // Consistent hashing for sticky sessions
        public Backend getBackendConsistentHash(String serviceName, String key) {
            List<Backend> backends = services.get(serviceName);
            if (backends == null || backends.isEmpty()) {
                return null;
            }
            
            int hash = Math.abs(key.hashCode());
            int index = hash % backends.size();
            
            for (int i = 0; i < backends.size(); i++) {
                Backend backend = backends.get((index + i) % backends.size());
                if (backend.isHealthy()) {
                    return backend;
                }
            }
            
            return null;
        }
    }
    
    static class Backend {
        final String url;
        final String name;
        private volatile boolean healthy = true;
        private final AtomicLong requestCount = new AtomicLong(0);
        
        public Backend(String url, String name) {
            this.url = url;
            this.name = name;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        public void markHealthy() {
            if (!healthy) {
                LOGGER.info("Backend " + url + " is now healthy");
            }
            healthy = true;
        }
        
        public void markUnhealthy() {
            if (healthy) {
                LOGGER.warning("Backend " + url + " is now unhealthy");
            }
            healthy = false;
        }
        
        public long incrementRequests() {
            return requestCount.incrementAndGet();
        }
    }
    
    // ============================================================================
    // Health Checker
    // ============================================================================
    
    static class HealthChecker {
        private final ScheduledExecutorService scheduler = 
            Executors.newScheduledThreadPool(5);
        private final Map<Backend, ScheduledFuture<?>> monitors = 
            new ConcurrentHashMap<>();
        
        public void monitor(Backend backend, String healthPath, int intervalSeconds) {
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> checkHealth(backend, healthPath),
                0,
                intervalSeconds,
                TimeUnit.SECONDS
            );
            
            monitors.put(backend, future);
        }
        
        private void checkHealth(Backend backend, String healthPath) {
            try {
                String healthURL = backend.url + healthPath;
                HttpURLConnection connection = (HttpURLConnection) 
                    new URL(healthURL).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode == 200) {
                    backend.markHealthy();
                } else {
                    backend.markUnhealthy();
                }
                
                connection.disconnect();
                
            } catch (IOException e) {
                backend.markUnhealthy();
            }
        }
        
        public void shutdown() {
            monitors.values().forEach(future -> future.cancel(true));
            scheduler.shutdown();
        }
    }
    
    // ============================================================================
    // Authentication
    // ============================================================================
    
    static class Authenticator {
        private final Key secretKey;
        
        public Authenticator(String secret) {
            this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        
        public Claims validateJWT(String token) throws JwtException {
            return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        }
        
        public String generateJWT(String userId, String role, List<String> scopes, 
                                 long durationSeconds) {
            Instant now = Instant.now();
            
            return Jwts.builder()
                .setSubject(userId)
                .claim("role", role)
                .claim("scopes", scopes)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(durationSeconds)))
                .signWith(secretKey)
                .compact();
        }
    }
    
    // ============================================================================
    // Rate Limiter (Token Bucket Algorithm)
    // ============================================================================
    
    static class RateLimiter {
        private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
        private final int maxRate;
        private final int windowSeconds;
        
        public RateLimiter(int maxRate, int windowSeconds) {
            this.maxRate = maxRate;
            this.windowSeconds = windowSeconds;
        }
        
        public boolean allow(String identifier) {
            TokenBucket bucket = buckets.computeIfAbsent(identifier, 
                k -> new TokenBucket(maxRate, (double) maxRate / windowSeconds));
            
            return bucket.consume(1);
        }
        
        public int getRemaining(String identifier) {
            TokenBucket bucket = buckets.get(identifier);
            return bucket != null ? (int) bucket.getTokens() : maxRate;
        }
    }
    
    static class TokenBucket {
        private double tokens;
        private long lastRefillTime;
        private final double capacity;
        private final double refillRate;
        
        public TokenBucket(double capacity, double refillRate) {
            this.tokens = capacity;
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.lastRefillTime = System.currentTimeMillis();
        }
        
        public synchronized boolean consume(double tokensToConsume) {
            refill();
            
            if (tokens >= tokensToConsume) {
                tokens -= tokensToConsume;
                return true;
            }
            
            return false;
        }
        
        private void refill() {
            long now = System.currentTimeMillis();
            double elapsed = (now - lastRefillTime) / 1000.0;
            tokens = Math.min(capacity, tokens + elapsed * refillRate);
            lastRefillTime = now;
        }
        
        public synchronized double getTokens() {
            refill();
            return tokens;
        }
    }
    
    // ============================================================================
    // Middleware
    // ============================================================================
    
    interface Middleware {
        boolean handle(Context context) throws IOException;
    }
    
    static class Context {
        final HttpExchange exchange;
        final Map<String, Object> attributes = new HashMap<>();
        
        public Context(HttpExchange exchange) {
            this.exchange = exchange;
        }
    }
    
    static class AuthMiddleware implements Middleware {
        private final Authenticator authenticator;
        
        public AuthMiddleware(Authenticator authenticator) {
            this.authenticator = authenticator;
        }
        
        @Override
        public boolean handle(Context context) throws IOException {
            HttpExchange exchange = context.exchange;
            String path = exchange.getRequestURI().getPath();
            
            // Skip auth for health endpoints
            if (path.endsWith("/health")) {
                return true;
            }
            
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendError(exchange, 401, "Authorization required");
                return false;
            }
            
            String token = authHeader.substring(7);
            
            try {
                Claims claims = authenticator.validateJWT(token);
                
                // Add user context to request
                exchange.getRequestHeaders().set("X-User-ID", claims.getSubject());
                exchange.getRequestHeaders().set("X-User-Role", 
                    claims.get("role", String.class));
                
                return true;
                
            } catch (JwtException e) {
                sendError(exchange, 401, "Invalid token");
                return false;
            }
        }
    }
    
    static class RateLimitMiddleware implements Middleware {
        private final RateLimiter rateLimiter;
        
        public RateLimitMiddleware(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
        }
        
        @Override
        public boolean handle(Context context) throws IOException {
            HttpExchange exchange = context.exchange;
            
            // Use user ID if available, otherwise IP
            String identifier = exchange.getRequestHeaders().getFirst("X-User-ID");
            if (identifier == null) {
                identifier = exchange.getRemoteAddress().getAddress().getHostAddress();
            }
            
            if (!rateLimiter.allow(identifier)) {
                exchange.getResponseHeaders().set("X-RateLimit-Remaining", "0");
                exchange.getResponseHeaders().set("Retry-After", "60");
                sendError(exchange, 429, "Rate limit exceeded");
                return false;
            }
            
            int remaining = rateLimiter.getRemaining(identifier);
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", 
                String.valueOf(remaining));
            
            return true;
        }
    }
    
    static class CORSMiddleware implements Middleware {
        @Override
        public boolean handle(Context context) throws IOException {
            HttpExchange exchange = context.exchange;
            
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", 
                "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", 
                "Content-Type, Authorization");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return false;
            }
            
            return true;
        }
    }
    
    // ============================================================================
    // Configuration
    // ============================================================================
    
    static class Config {
        int port = 8080;
        String jwtSecret = "your-secret-key-change-in-production";
        int rateLimitMax = 100;
        int rateLimitWindowSeconds = 60;
    }
    
    static class ServiceConfig {
        String name;
        List<String> backends;
        String healthURL;
        int rateLimit;
        
        public ServiceConfig(String name, List<String> backends, String healthURL) {
            this.name = name;
            this.backends = backends;
            this.healthURL = healthURL;
            this.rateLimit = 100;
        }
    }
    
    // ============================================================================
    // Main
    // ============================================================================
    
    public static void main(String[] args) throws IOException {
        // Configure logging
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.INFO);
        
        // Create configuration
        Config config = new Config();
        config.port = 8080;
        config.jwtSecret = "your-secret-key-change-in-production";
        config.rateLimitMax = 100;
        config.rateLimitWindowSeconds = 60;
        
        // Create gateway
        API_Gateway_Java gateway = new API_Gateway_Java(config);
        
        // Register middleware
        gateway.use(new CORSMiddleware());
        gateway.use(new RateLimitMiddleware(gateway.rateLimiter));
        gateway.use(new AuthMiddleware(gateway.authenticator));
        
        // Register services
        gateway.registerService("/api/users", new ServiceConfig(
            "user-service",
            Arrays.asList("http://localhost:3001", "http://localhost:3002"),
            "/health"
        ));
        
        gateway.registerService("/api/orders", new ServiceConfig(
            "order-service",
            Arrays.asList("http://localhost:4001", "http://localhost:4002"),
            "/health"
        ));
        
        gateway.registerService("/api/products", new ServiceConfig(
            "product-service",
            Arrays.asList("http://localhost:5001"),
            "/health"
        ));
        
        // Start gateway
        gateway.start();
        
        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down API Gateway...");
            gateway.stop();
        }));
    }
}
