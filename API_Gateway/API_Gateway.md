# API Gateway: A Complete Technical Guide

## Table of Contents
1. [Fundamentals](#fundamentals)
2. [Core Functions](#core-functions)
3. [Architecture Patterns](#architecture-patterns)
4. [Routing & Load Balancing](#routing--load-balancing)
5. [Authentication & Authorization](#authentication--authorization)
6. [Rate Limiting & Throttling](#rate-limiting--throttling)
7. [Request/Response Transformation](#requestresponse-transformation)
8. [Caching Strategies](#caching-strategies)
9. [Service Discovery](#service-discovery)
10. [Observability & Monitoring](#observability--monitoring)
11. [Security](#security)
12. [Technologies & Tools](#technologies--tools)
13. [Performance Optimization](#performance-optimization)
14. [Anti-Patterns & Pitfalls](#anti-patterns--pitfalls)
15. [Real-World Examples](#real-world-examples)

---

## Fundamentals

### What is an API Gateway?

An API Gateway is a server that acts as a single entry point for a collection of microservices. It sits between clients and backend services, routing requests, enforcing policies, and aggregating responses.

**Core Purpose:**
- **Single Entry Point**: Unified interface for all clients
- **Request Routing**: Direct requests to appropriate backend services
- **Protocol Translation**: Convert between protocols (HTTP, gRPC, WebSocket)
- **Cross-Cutting Concerns**: Handle authentication, logging, rate limiting centrally

**Analogy:** Like a hotel concierge - clients ask the gateway for services, and it directs them to the right department without clients needing to know internal structure.

### Why Use an API Gateway?

#### Benefits

1. **Simplified Client Logic**
   - Clients interact with single endpoint
   - No need to track multiple service URLs
   - Reduces client-side complexity

2. **Centralized Cross-Cutting Concerns**
   - Authentication/Authorization in one place
   - Rate limiting applied uniformly
   - Logging and monitoring centralized
   - SSL/TLS termination

3. **Backend Flexibility**
   - Change backend services without affecting clients
   - Version APIs independently
   - A/B testing and canary deployments

4. **Protocol Translation**
   - Convert HTTP to gRPC
   - Support WebSockets for real-time features
   - Legacy protocol support

5. **Request Aggregation**
   - Combine multiple backend calls into one
   - Reduce client-server round trips
   - Optimize mobile/slow connections

6. **Security**
   - Hide internal service topology
   - Prevent direct backend access
   - DDoS protection
   - Request validation

#### Trade-offs

**Cons:**
- ❌ **Single Point of Failure**: If gateway goes down, all services affected
- ❌ **Latency**: Additional network hop adds latency (typically 1-10ms)
- ❌ **Complexity**: Another component to maintain and scale
- ❌ **Bottleneck**: Can become performance bottleneck at high scale
- ❌ **Cost**: Additional infrastructure and operational overhead

### When to Use an API Gateway

✅ **Good Fit:**
- Microservices architecture
- Multiple client types (web, mobile, IoT)
- Need centralized authentication
- Rate limiting required
- Legacy system integration
- Public-facing APIs

❌ **May Not Need:**
- Simple monolithic application
- Internal-only services with trusted clients
- Ultra-low latency requirements (every millisecond counts)
- Very small scale (few services)

---

## Core Functions

### 1. Request Routing

Route incoming requests to appropriate backend services based on:
- URL path
- HTTP method
- Headers
- Query parameters
- Request body content

**Example:**
```
GET  /users/*      → User Service
POST /orders       → Order Service
GET  /products/*   → Product Service
GET  /search       → Search Service
```

**Advanced Routing:**
```yaml
# Path-based
/api/v1/users/*     → User Service v1
/api/v2/users/*     → User Service v2

# Header-based
User-Agent: Mobile  → Mobile-optimized service
User-Agent: Web     → Web service

# Weight-based (Canary)
/api/users → 90% old version, 10% new version
```

### 2. Load Balancing

Distribute traffic across multiple instances of a service.

**Algorithms:**
- **Round Robin**: Requests distributed sequentially
- **Least Connections**: Send to instance with fewest active connections
- **Weighted Round Robin**: Distribute based on instance capacity
- **IP Hash**: Same client always routes to same instance
- **Random**: Random selection
- **Least Response Time**: Send to fastest responding instance

**Health Checks:**
```yaml
health_check:
  path: /health
  interval: 10s
  timeout: 5s
  healthy_threshold: 2
  unhealthy_threshold: 3
```

### 3. Authentication & Authorization

Verify client identity and permissions before forwarding requests.

**Authentication Methods:**
- **API Keys**: Simple token in header
- **JWT (JSON Web Tokens)**: Stateless, encoded user claims
- **OAuth 2.0**: Delegated authorization
- **Basic Auth**: Username/password (legacy)
- **mTLS**: Mutual TLS certificate authentication

**Authorization:**
- **RBAC (Role-Based Access Control)**: Permissions based on roles
- **ABAC (Attribute-Based Access Control)**: Permissions based on attributes
- **Scope-Based**: OAuth scopes limit access

**Flow:**
```
1. Client sends request with credentials
2. Gateway validates credentials (check cache or auth service)
3. If valid, extract user context (ID, roles, permissions)
4. Check if user authorized for requested resource
5. Forward request with user context to backend
6. Return response or 401/403 error
```

### 4. Rate Limiting

Control request rate per client to prevent abuse and ensure fair usage.

**Strategies:**
- **Per User**: Limit based on user ID
- **Per IP**: Limit based on client IP
- **Per API Key**: Limit based on API key
- **Global**: Limit total traffic to backend
- **Tiered**: Different limits for free/premium users

**Algorithms:** (See Rate_Limiting.md for details)
- Token Bucket
- Leaky Bucket
- Fixed Window
- Sliding Window

**Response:**
```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1683561600
Retry-After: 60
```

### 5. Request/Response Transformation

Modify requests and responses as they pass through.

**Request Transformation:**
- Add/remove headers
- Modify request body
- Change URL path
- Add authentication tokens
- Protocol conversion (REST to gRPC)

**Response Transformation:**
- Add/remove headers
- Filter sensitive fields
- Aggregate multiple responses
- Format conversion (XML to JSON)
- Error message standardization

**Example:**
```javascript
// Add correlation ID
request.headers['X-Correlation-ID'] = generateUUID();

// Remove sensitive fields
delete response.body.user.ssn;
delete response.body.user.password_hash;

// Standardize error format
if (response.status >= 400) {
  response.body = {
    error: {
      code: response.status,
      message: response.body.message,
      timestamp: Date.now()
    }
  };
}
```

### 6. Caching

Cache responses to reduce backend load and improve latency.

**Cache Strategies:**
- **Pass-through**: Cache at gateway level
- **Private**: User-specific cache
- **Shared**: Cache shared across users
- **Edge**: Cache at CDN edge locations

**Cache Key Design:**
```
cache_key = hash(
  method + 
  path + 
  query_params + 
  user_id +  // for private cache
  headers['Accept-Language']
)
```

**Cache Headers:**
```http
Cache-Control: public, max-age=3600
ETag: "33a64df551425fcc55e4d42a148795d9f25f89d4"
Last-Modified: Wed, 21 Oct 2015 07:28:00 GMT
```

### 7. Protocol Translation

Convert between different protocols.

**Common Translations:**
- **HTTP to gRPC**: REST API frontend, efficient gRPC backend
- **HTTP/1.1 to HTTP/2**: Upgrade for multiplexing
- **REST to GraphQL**: Expose GraphQL API over REST services
- **WebSocket to HTTP**: Real-time features over traditional APIs
- **SOAP to REST**: Modernize legacy SOAP services

### 8. Request Aggregation (Backend for Frontend)

Combine multiple backend requests into single client request.

**Example:**
```javascript
// Client makes one request
GET /api/dashboard

// Gateway makes multiple requests
const [user, orders, recommendations] = await Promise.all([
  fetch('user-service/users/123'),
  fetch('order-service/orders?user_id=123'),
  fetch('recommendation-service/recommend?user_id=123')
]);

// Gateway combines and returns
return {
  user: user,
  recentOrders: orders.slice(0, 5),
  recommendations: recommendations
};
```

**Benefits:**
- Reduces client round trips
- Optimizes mobile/slow connections
- Simplifies client logic
- Reduces payload size

### 9. Service Discovery Integration

Dynamically discover and route to service instances.

**Methods:**
- **Client-Side Discovery**: Gateway queries service registry
- **Server-Side Discovery**: Load balancer handles discovery
- **DNS-Based**: Use DNS for service resolution
- **Consul/Eureka Integration**: Integrate with service registry

**Flow:**
```
1. Service registers with registry (Consul, Eureka, etcd)
2. Gateway queries registry for service location
3. Gateway caches service locations (with TTL)
4. Gateway routes request to healthy instance
5. Gateway updates cache on health check failures
```

### 10. Observability

Collect metrics, logs, and traces for monitoring and debugging.

**Metrics:**
- Request count, rate
- Response latency (p50, p95, p99)
- Error rate
- Backend service health
- Cache hit ratio

**Logging:**
- Access logs (every request)
- Error logs
- Audit logs (authentication/authorization)
- Debug logs (detailed for troubleshooting)

**Distributed Tracing:**
```
Client Request → Gateway → Service A → Service B
      |            |          |           |
   trace_id     span_1     span_2     span_3
```

**Tools:** Prometheus, Grafana, ELK Stack, Jaeger, Zipkin, Datadog

---

## Architecture Patterns

### 1. Single Gateway (Simple)

```
           ┌─────────────┐
Clients → │ API Gateway │ → ┌─────────┐
           └─────────────┘   │Service A│
                             ├─────────┤
                             │Service B│
                             ├─────────┤
                             │Service C│
                             └─────────┘
```

**Pros:**
- Simple architecture
- Easy to manage
- Centralized control

**Cons:**
- Single point of failure
- Can become bottleneck
- All traffic through one component

**Use Case:** Small to medium applications

### 2. Backend for Frontend (BFF)

```
Web App    → ┌───────────┐ → ┌─────────┐
              │ Web BFF   │    │Service A│
Mobile App → ├───────────┤    ├─────────┤
              │Mobile BFF │    │Service B│
IoT Device → ├───────────┤    ├─────────┤
              │ IoT BFF   │    │Service C│
              └───────────┘    └─────────┘
```

**Characteristics:**
- Separate gateway per client type
- Optimized aggregation per client
- Independent scaling
- Client-specific transformations

**Pros:**
- Tailored to client needs
- Better performance per client type
- Isolated failures

**Cons:**
- Code duplication
- More gateways to manage

**Use Case:** Multiple client types with different needs

### 3. Microgateway Pattern

```
           ┌─────────┐
Clients → │ Gateway │ → ┌──────┐ → ┌─────────┐
           └─────────┘   │ µGW  │   │Service A│
                         ├──────┤   ├─────────┤
                         │ µGW  │   │Service B│
                         ├──────┤   ├─────────┤
                         │ µGW  │   │Service C│
                         └──────┘   └─────────┘
```

**Characteristics:**
- Lightweight gateway per service/team
- Deployed alongside services
- Decentralized policy enforcement

**Pros:**
- Team autonomy
- Service-specific policies
- Reduced blast radius

**Cons:**
- Policy duplication
- Harder to enforce global policies
- More components

**Use Case:** Large organizations, team autonomy

### 4. Edge Gateway + Internal Gateway

```
                       ┌──────────┐
Internet → ┌──────────┐│ Internal │ → ┌─────────┐
           │Edge GW   ││ Gateway  │   │Service A│
           └──────────┘└──────────┘   ├─────────┤
           (Public)    (Private)      │Service B│
                                      └─────────┘
```

**Characteristics:**
- Edge gateway: public-facing, security, rate limiting
- Internal gateway: service routing, service mesh integration

**Pros:**
- Security isolation
- Specialized concerns per layer
- Flexible scaling

**Cons:**
- Additional latency
- More complex architecture

**Use Case:** Large enterprises, high security requirements

### 5. Service Mesh Integration

```
Clients → ┌─────────┐   ┌─────────────────┐
          │ Gateway │   │   Service Mesh  │
          └─────────┘   │  (Istio/Linkerd)│
                        │                 │
                        │ ┌────┐   ┌────┐│
                        │ │Svc │←→│Svc ││
                        │ │ A  │   │ B  ││
                        │ └────┘   └────┘│
                        └─────────────────┘
```

**Characteristics:**
- Gateway handles external traffic
- Service mesh handles service-to-service communication
- Complementary, not overlapping

**Responsibilities:**
- **Gateway**: Authentication, rate limiting, public API
- **Mesh**: mTLS, circuit breaking, internal observability

---

## Routing & Load Balancing

### Routing Strategies

#### 1. Path-Based Routing

```nginx
location /api/users {
    proxy_pass http://user-service;
}

location /api/orders {
    proxy_pass http://order-service;
}

location /api/products {
    proxy_pass http://product-service;
}
```

#### 2. Host-Based Routing (Virtual Hosting)

```
api.example.com     → API Gateway (Production)
api-dev.example.com → API Gateway (Development)
api-v2.example.com  → API Gateway (v2)
```

#### 3. Header-Based Routing

```javascript
if (request.headers['X-API-Version'] === 'v2') {
  route to v2 services
} else {
  route to v1 services
}

if (request.headers['X-Region'] === 'us-west') {
  route to us-west cluster
}
```

#### 4. Query Parameter Routing

```
/api/users?version=v2 → User Service v2
/api/users?version=v1 → User Service v1
```

#### 5. Content-Based Routing

```javascript
// Route based on request body
if (request.body.orderType === 'subscription') {
  route to subscription-service
} else if (request.body.orderType === 'one-time') {
  route to order-service
}
```

### Load Balancing Algorithms

#### 1. Round Robin

```
Request 1 → Server A
Request 2 → Server B
Request 3 → Server C
Request 4 → Server A (repeats)
```

**Pros:** Simple, fair distribution  
**Cons:** Doesn't consider server load or capacity

#### 2. Weighted Round Robin

```
Server A (weight: 3): 60% of traffic
Server B (weight: 2): 40% of traffic
```

**Use Case:** Servers with different capacities

#### 3. Least Connections

```
Server A: 10 active connections
Server B: 5 active connections  ← Route here
Server C: 15 active connections
```

**Pros:** Considers current load  
**Cons:** More complex tracking

#### 4. Least Response Time

```
Server A: avg 50ms
Server B: avg 20ms  ← Route here
Server C: avg 100ms
```

**Pros:** Performance-aware  
**Cons:** Requires latency monitoring

#### 5. IP Hash (Session Affinity)

```
hash(client_ip) % num_servers = target_server

Client 192.168.1.10 → Always Server B
Client 192.168.1.11 → Always Server A
```

**Pros:** Session persistence  
**Cons:** Uneven distribution, sticky sessions

#### 6. Consistent Hashing

```
Servers and keys hashed onto ring [0, 2^32-1]
Key routes to first server clockwise
```

**Pros:** Minimal redistribution on server changes  
**Use Case:** Distributed caching, sharding

### Health Checks

#### Active Health Checks

```yaml
health_check:
  type: http
  path: /health
  interval: 10s
  timeout: 5s
  healthy_threshold: 2
  unhealthy_threshold: 3
  expected_response: 200
```

**Gateway behavior:**
```
Every 10 seconds:
  Send GET /health to each server
  If 200 response within 5s:
    Mark as healthy (after 2 consecutive successes)
  Else:
    Mark as unhealthy (after 3 consecutive failures)
```

#### Passive Health Checks (Circuit Breaker)

```
Monitor actual request failures:
  If error_rate > 50% over 10 requests:
    Open circuit (stop routing)
  After 30s:
    Try one request (half-open)
  If success:
    Close circuit (resume routing)
```

---

## Authentication & Authorization

### Authentication Patterns

#### 1. API Key Authentication

**Flow:**
```
1. Client includes API key in header
   Authorization: ApiKey abc123def456

2. Gateway validates key (cache or database)
3. Forward request with user context
```

**Implementation:**
```javascript
async function validateApiKey(apiKey) {
  // Check cache first
  let userId = await cache.get(`apikey:${apiKey}`);
  
  if (!userId) {
    // Query database
    const user = await db.query(
      'SELECT user_id FROM api_keys WHERE key = ? AND active = true',
      [apiKey]
    );
    
    if (!user) return null;
    
    userId = user.user_id;
    await cache.set(`apikey:${apiKey}`, userId, 300); // 5 min TTL
  }
  
  return userId;
}
```

**Pros:** Simple, stateless  
**Cons:** Key compromise affects all requests, no expiration

#### 2. JWT (JSON Web Token)

**Token Structure:**
```
Header.Payload.Signature

Header: {"alg": "HS256", "typ": "JWT"}
Payload: {"sub": "user123", "role": "admin", "exp": 1683561600}
Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
```

**Flow:**
```
1. Client authenticates with auth service
2. Auth service returns JWT
3. Client includes JWT in requests
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

4. Gateway validates JWT signature
5. Extracts claims (user_id, roles, expiration)
6. Checks expiration
7. Forwards request with user context
```

**Validation:**
```javascript
const jwt = require('jsonwebtoken');

function validateJWT(token, secret) {
  try {
    const decoded = jwt.verify(token, secret);
    
    // Check custom claims
    if (decoded.exp < Date.now() / 1000) {
      throw new Error('Token expired');
    }
    
    return decoded; // {sub: "user123", role: "admin", ...}
  } catch (err) {
    return null;
  }
}
```

**Pros:** Stateless, contains user context, can expire  
**Cons:** Token size, cannot revoke before expiration

#### 3. OAuth 2.0

**Authorization Code Flow:**
```
1. Client redirects user to authorization server
   https://auth.example.com/authorize?client_id=...&redirect_uri=...

2. User authenticates and grants permission
3. Auth server redirects back with authorization code
   https://client.com/callback?code=abc123

4. Client exchanges code for access token
   POST /token with code + client_secret

5. Client includes access token in API requests
   Authorization: Bearer access_token

6. Gateway validates token with auth server (or introspection endpoint)
7. Forward request if valid
```

**Token Introspection:**
```javascript
async function validateOAuthToken(accessToken) {
  const response = await fetch('https://auth.example.com/introspect', {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: `token=${accessToken}`
  });
  
  const data = await response.json();
  
  if (!data.active) return null;
  
  return {
    userId: data.sub,
    scopes: data.scope.split(' '),
    expiresAt: data.exp
  };
}
```

**Pros:** Industry standard, delegated authorization, refresh tokens  
**Cons:** Complex, requires auth server

#### 4. mTLS (Mutual TLS)

**Flow:**
```
1. Client and server present certificates
2. Both verify each other's certificate
3. Establish encrypted connection
4. Gateway extracts client identity from certificate
```

**Configuration (Nginx):**
```nginx
server {
  listen 443 ssl;
  
  ssl_certificate /path/to/server.crt;
  ssl_certificate_key /path/to/server.key;
  
  ssl_client_certificate /path/to/ca.crt;
  ssl_verify_client on;
  
  location / {
    # Client cert validated, extract identity
    proxy_set_header X-Client-DN $ssl_client_s_dn;
    proxy_pass http://backend;
  }
}
```

**Pros:** Strongest authentication, no credentials in application layer  
**Cons:** Certificate management complexity, client setup

### Authorization Patterns

#### 1. Role-Based Access Control (RBAC)

```javascript
const permissions = {
  admin: ['read', 'write', 'delete', 'manage_users'],
  editor: ['read', 'write'],
  viewer: ['read']
};

function authorize(userRole, requiredPermission) {
  return permissions[userRole]?.includes(requiredPermission);
}

// Middleware
app.use((req, res, next) => {
  const user = req.user; // from JWT
  const requiredPerm = routePermissions[req.path];
  
  if (!authorize(user.role, requiredPerm)) {
    return res.status(403).json({error: 'Forbidden'});
  }
  
  next();
});
```

#### 2. Attribute-Based Access Control (ABAC)

```javascript
function evaluate(policy, context) {
  // Policy: User can access resource if:
  // - User's department matches resource department
  // - Time is during business hours
  // - Resource is not marked confidential or user has clearance
  
  const { user, resource, time } = context;
  
  if (user.department !== resource.department) return false;
  
  if (time.hour < 9 || time.hour > 17) return false;
  
  if (resource.confidential && !user.clearances.includes('confidential')) {
    return false;
  }
  
  return true;
}
```

#### 3. Scope-Based (OAuth)

```javascript
// JWT contains scopes
{
  "sub": "user123",
  "scopes": ["read:users", "write:posts", "delete:comments"]
}

// Middleware checks scopes
function requireScope(requiredScope) {
  return (req, res, next) => {
    const userScopes = req.user.scopes;
    
    if (!userScopes.includes(requiredScope)) {
      return res.status(403).json({
        error: 'Insufficient scope',
        required: requiredScope
      });
    }
    
    next();
  };
}

app.get('/api/users', requireScope('read:users'), handleGetUsers);
app.post('/api/posts', requireScope('write:posts'), handleCreatePost);
```

### Best Practices

1. **Cache Authentication Results**: Reduce auth service load
2. **Use Short-Lived Tokens**: JWT with 15-60 min expiration
3. **Implement Token Refresh**: Refresh tokens for long sessions
4. **Audit Logs**: Log all authentication/authorization events
5. **Fail Closed**: Deny access on auth service failure (or use cached decisions)
6. **Rate Limit Auth Endpoints**: Prevent brute force
7. **Use HTTPS**: Always encrypt credentials in transit

---

## Rate Limiting & Throttling

### Implementation at Gateway Level

#### Redis-Based Rate Limiter

```javascript
const redis = require('redis');
const client = redis.createClient();

async function rateLimitMiddleware(req, res, next) {
  const identifier = req.user?.id || req.ip;
  const key = `rate_limit:${identifier}`;
  const limit = 100; // requests
  const window = 60; // seconds
  
  const current = await client.incr(key);
  
  if (current === 1) {
    await client.expire(key, window);
  }
  
  const ttl = await client.ttl(key);
  
  res.setHeader('X-RateLimit-Limit', limit);
  res.setHeader('X-RateLimit-Remaining', Math.max(0, limit - current));
  res.setHeader('X-RateLimit-Reset', Date.now() + ttl * 1000);
  
  if (current > limit) {
    return res.status(429).json({
      error: 'Too many requests',
      retryAfter: ttl
    });
  }
  
  next();
}
```

#### Sliding Window with Redis

```lua
-- Lua script for atomic sliding window
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Remove old entries
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- Count current entries
local current = redis.call('ZCARD', key)

if current < limit then
    -- Add new entry
    redis.call('ZADD', key, now, now)
    redis.call('EXPIRE', key, window)
    return {1, limit - current - 1}
else
    return {0, 0}
end
```

```javascript
async function slidingWindowRateLimit(userId, limit, window) {
  const key = `rate_limit:${userId}`;
  const now = Date.now();
  
  const result = await redis.eval(
    luaScript,
    1,
    key,
    limit,
    window * 1000,
    now
  );
  
  const [allowed, remaining] = result;
  return { allowed: allowed === 1, remaining };
}
```

### Hierarchical Rate Limiting

```javascript
async function checkAllLimits(req, res) {
  const checks = [
    // Global limit
    checkLimit('global', 10000, 60),
    
    // Per user limit
    checkLimit(`user:${req.user.id}`, 100, 60),
    
    // Per IP limit
    checkLimit(`ip:${req.ip}`, 50, 60),
    
    // Per endpoint limit
    checkLimit(`endpoint:${req.path}`, 20, 60)
  ];
  
  const results = await Promise.all(checks);
  
  for (const result of results) {
    if (!result.allowed) {
      return res.status(429).json({
        error: 'Rate limit exceeded',
        limit: result.limit,
        retryAfter: result.retryAfter
      });
    }
  }
  
  return true;
}
```

### Adaptive Rate Limiting

```javascript
// Adjust limits based on backend health
async function getAdaptiveLimit(baseLimit) {
  const backendHealth = await getBackendHealth();
  
  if (backendHealth.cpuUsage > 80) {
    return baseLimit * 0.5; // Reduce by 50%
  } else if (backendHealth.cpuUsage > 60) {
    return baseLimit * 0.75; // Reduce by 25%
  }
  
  return baseLimit;
}
```

---

## Request/Response Transformation

### Request Transformation

#### Header Manipulation

```javascript
// Add correlation ID for tracing
if (!req.headers['x-correlation-id']) {
  req.headers['x-correlation-id'] = generateUUID();
}

// Add authentication context
req.headers['x-user-id'] = req.user.id;
req.headers['x-user-role'] = req.user.role;

// Remove sensitive headers before forwarding
delete req.headers['authorization'];
delete req.headers['cookie'];
```

#### Body Transformation

```javascript
// Add metadata to request
if (req.method === 'POST' || req.method === 'PUT') {
  req.body.metadata = {
    timestamp: Date.now(),
    source: 'api-gateway',
    userId: req.user.id
  };
}

// Convert formats
if (req.headers['content-type'] === 'application/xml') {
  req.body = xmlToJson(req.body);
  req.headers['content-type'] = 'application/json';
}
```

#### URL Rewriting

```javascript
// Remove /api prefix before forwarding to backend
const originalPath = '/api/v1/users/123';
const backendPath = originalPath.replace(/^\/api\/v1/, '');
// → '/users/123'

// Add query parameters
req.url += `?gateway_id=gw-001&timestamp=${Date.now()}`;
```

### Response Transformation

#### Field Filtering

```javascript
// Remove sensitive fields
function filterResponse(response, userRole) {
  const sensitiveFields = ['ssn', 'password_hash', 'internal_id'];
  
  if (userRole !== 'admin') {
    sensitiveFields.forEach(field => {
      delete response[field];
    });
  }
  
  return response;
}
```

#### Response Aggregation

```javascript
async function aggregateUserDashboard(userId) {
  // Make parallel requests to multiple services
  const [user, orders, recommendations, notifications] = await Promise.allSettled([
    fetchUserProfile(userId),
    fetchRecentOrders(userId),
    fetchRecommendations(userId),
    fetchNotifications(userId)
  ]);
  
  // Combine results
  return {
    user: user.status === 'fulfilled' ? user.value : null,
    recentOrders: orders.status === 'fulfilled' ? orders.value : [],
    recommendations: recommendations.status === 'fulfilled' ? recommendations.value : [],
    notifications: notifications.status === 'fulfilled' ? notifications.value : [],
    timestamp: Date.now()
  };
}
```

#### Error Standardization

```javascript
function standardizeError(error, statusCode) {
  return {
    error: {
      code: statusCode,
      message: error.message,
      details: process.env.NODE_ENV === 'development' ? error.stack : undefined,
      timestamp: new Date().toISOString(),
      path: req.path,
      requestId: req.headers['x-correlation-id']
    }
  };
}

// Middleware
app.use((err, req, res, next) => {
  const statusCode = err.statusCode || 500;
  res.status(statusCode).json(standardizeError(err, statusCode));
});
```

#### Response Compression

```javascript
const compression = require('compression');

app.use(compression({
  filter: (req, res) => {
    if (req.headers['x-no-compression']) {
      return false;
    }
    return compression.filter(req, res);
  },
  threshold: 1024 // Only compress responses > 1KB
}));
```

---

## Caching Strategies

### Gateway-Level Caching

#### Cache Key Generation

```javascript
function generateCacheKey(req) {
  const components = [
    req.method,
    req.path,
    JSON.stringify(req.query),
    req.user?.id || req.ip,
    req.headers['accept-language']
  ];
  
  return crypto.createHash('sha256')
    .update(components.join(':'))
    .digest('hex');
}
```

#### Cache Middleware

```javascript
const cacheMiddleware = (ttl = 300) => async (req, res, next) => {
  // Only cache GET requests
  if (req.method !== 'GET') {
    return next();
  }
  
  const cacheKey = generateCacheKey(req);
  
  // Check cache
  const cached = await redis.get(cacheKey);
  
  if (cached) {
    const data = JSON.parse(cached);
    res.setHeader('X-Cache', 'HIT');
    res.setHeader('Age', Date.now() - data.timestamp);
    return res.json(data.body);
  }
  
  // Intercept response
  const originalJson = res.json.bind(res);
  res.json = (body) => {
    // Cache response
    redis.setex(cacheKey, ttl, JSON.stringify({
      body,
      timestamp: Date.now()
    }));
    
    res.setHeader('X-Cache', 'MISS');
    return originalJson(body);
  };
  
  next();
};

// Usage
app.get('/api/products', cacheMiddleware(300), handleGetProducts);
```

### Cache Invalidation

#### Time-Based (TTL)

```javascript
// Set expiration
redis.setex(`cache:${key}`, 3600, value); // 1 hour
```

#### Event-Based

```javascript
// Invalidate on data update
async function updateProduct(productId, data) {
  await db.update('products', productId, data);
  
  // Invalidate related caches
  await redis.del(`product:${productId}`);
  await redis.del(`products:list:*`); // Pattern delete
  
  // Publish invalidation event
  await redis.publish('cache:invalidate', JSON.stringify({
    type: 'product',
    id: productId
  }));
}
```

#### Tag-Based

```javascript
// Associate cache entries with tags
await redis.sadd('tag:products', cacheKey1, cacheKey2, cacheKey3);

// Invalidate all entries with tag
async function invalidateTag(tag) {
  const keys = await redis.smembers(`tag:${tag}`);
  if (keys.length > 0) {
    await redis.del(...keys);
    await redis.del(`tag:${tag}`);
  }
}
```

### Conditional Caching

```javascript
const shouldCache = (req, res) => {
  // Don't cache authenticated user-specific data
  if (req.user) return false;
  
  // Don't cache non-200 responses
  if (res.statusCode !== 200) return false;
  
  // Don't cache large responses
  if (res.get('content-length') > 1024 * 1024) return false;
  
  return true;
};
```

### Cache Warming

```javascript
// Pre-populate cache with frequently accessed data
async function warmCache() {
  const popularProducts = await db.query(
    'SELECT id FROM products ORDER BY views DESC LIMIT 100'
  );
  
  for (const product of popularProducts) {
    const data = await fetchProductData(product.id);
    const cacheKey = `product:${product.id}`;
    await redis.setex(cacheKey, 3600, JSON.stringify(data));
  }
}

// Run on gateway startup
warmCache();
```

---

## Service Discovery

### Static Configuration

```yaml
# config.yml
services:
  user-service:
    hosts:
      - http://user-1.internal:3000
      - http://user-2.internal:3000
      - http://user-3.internal:3000
  order-service:
    hosts:
      - http://order-1.internal:3001
      - http://order-2.internal:3001
```

**Pros:** Simple, no dependencies  
**Cons:** Manual updates, no dynamic scaling

### DNS-Based Discovery

```javascript
const dns = require('dns').promises;

async function discoverService(serviceName) {
  // DNS SRV records
  const records = await dns.resolveSrv(`_http._tcp.${serviceName}.internal`);
  
  return records.map(r => ({
    host: r.name,
    port: r.port,
    weight: r.weight,
    priority: r.priority
  }));
}
```

### Consul Integration

```javascript
const consul = require('consul')();

async function getServiceInstances(serviceName) {
  const result = await consul.health.service({
    service: serviceName,
    passing: true // Only healthy instances
  });
  
  return result.map(entry => ({
    id: entry.Service.ID,
    address: entry.Service.Address,
    port: entry.Service.Port,
    tags: entry.Service.Tags
  }));
}

// Cache with TTL
const serviceCache = new Map();

async function getServiceWithCache(serviceName) {
  if (serviceCache.has(serviceName)) {
    const cached = serviceCache.get(serviceName);
    if (Date.now() - cached.timestamp < 10000) { // 10s TTL
      return cached.instances;
    }
  }
  
  const instances = await getServiceInstances(serviceName);
  serviceCache.set(serviceName, {
    instances,
    timestamp: Date.now()
  });
  
  return instances;
}
```

### Kubernetes Service Discovery

```javascript
// In Kubernetes, use service DNS
const serviceUrl = `http://${serviceName}.${namespace}.svc.cluster.local:${port}`;

// Or use Kubernetes API
const k8s = require('@kubernetes/client-node');
const kc = new k8s.KubeConfig();
kc.loadFromDefault();

const api = kc.makeApiClient(k8s.CoreV1Api);

async function getServiceEndpoints(serviceName, namespace) {
  const res = await api.readNamespacedEndpoints(serviceName, namespace);
  
  const addresses = [];
  res.body.subsets?.forEach(subset => {
    subset.addresses?.forEach(addr => {
      subset.ports?.forEach(port => {
        addresses.push(`http://${addr.ip}:${port.port}`);
      });
    });
  });
  
  return addresses;
}
```

---

## Observability & Monitoring

### Metrics Collection

#### Request Metrics

```javascript
const prometheus = require('prom-client');

// Define metrics
const httpRequestDuration = new prometheus.Histogram({
  name: 'http_request_duration_seconds',
  help: 'Duration of HTTP requests in seconds',
  labelNames: ['method', 'route', 'status_code'],
  buckets: [0.001, 0.01, 0.1, 0.5, 1, 2, 5]
});

const httpRequestTotal = new prometheus.Counter({
  name: 'http_requests_total',
  help: 'Total number of HTTP requests',
  labelNames: ['method', 'route', 'status_code']
});

const httpRequestsInFlight = new prometheus.Gauge({
  name: 'http_requests_in_flight',
  help: 'Number of HTTP requests currently being processed'
});

// Middleware
app.use((req, res, next) => {
  const start = Date.now();
  httpRequestsInFlight.inc();
  
  res.on('finish', () => {
    const duration = (Date.now() - start) / 1000;
    
    httpRequestDuration.observe(
      { method: req.method, route: req.route?.path || req.path, status_code: res.statusCode },
      duration
    );
    
    httpRequestTotal.inc({
      method: req.method,
      route: req.route?.path || req.path,
      status_code: res.statusCode
    });
    
    httpRequestsInFlight.dec();
  });
  
  next();
});

// Expose metrics
app.get('/metrics', async (req, res) => {
  res.set('Content-Type', prometheus.register.contentType);
  res.end(await prometheus.register.metrics());
});
```

#### Backend Service Health

```javascript
const backendHealth = new prometheus.Gauge({
  name: 'backend_service_health',
  help: 'Health status of backend services (1=healthy, 0=unhealthy)',
  labelNames: ['service']
});

async function monitorBackendHealth() {
  const services = ['user-service', 'order-service', 'product-service'];
  
  for (const service of services) {
    try {
      const response = await fetch(`http://${service}/health`, { timeout: 5000 });
      backendHealth.set({ service }, response.ok ? 1 : 0);
    } catch (err) {
      backendHealth.set({ service }, 0);
    }
  }
}

// Poll every 10 seconds
setInterval(monitorBackendHealth, 10000);
```

### Distributed Tracing

#### OpenTelemetry Integration

```javascript
const { trace, context } = require('@opentelemetry/api');
const { NodeTracerProvider } = require('@opentelemetry/sdk-trace-node');
const { JaegerExporter } = require('@opentelemetry/exporter-jaeger');

// Setup
const provider = new NodeTracerProvider();
const exporter = new JaegerExporter({
  endpoint: 'http://jaeger:14268/api/traces'
});
provider.addSpanProcessor(new SimpleSpanProcessor(exporter));
provider.register();

const tracer = trace.getTracer('api-gateway');

// Tracing middleware
app.use((req, res, next) => {
  const span = tracer.startSpan('http.request', {
    attributes: {
      'http.method': req.method,
      'http.url': req.url,
      'http.target': req.path
    }
  });
  
  // Inject trace context
  req.traceId = span.spanContext().traceId;
  req.spanId = span.spanContext().spanId;
  
  res.on('finish', () => {
    span.setAttributes({
      'http.status_code': res.statusCode
    });
    
    if (res.statusCode >= 400) {
      span.setStatus({ code: 2, message: 'Error' });
    }
    
    span.end();
  });
  
  // Continue in span context
  context.with(trace.setSpan(context.active(), span), next);
});

// Propagate trace to backend
async function callBackend(url, options = {}) {
  const span = tracer.startSpan('backend.request');
  
  options.headers = options.headers || {};
  options.headers['traceparent'] = `00-${span.spanContext().traceId}-${span.spanContext().spanId}-01`;
  
  try {
    const response = await fetch(url, options);
    span.setAttributes({
      'http.status_code': response.status
    });
    return response;
  } catch (err) {
    span.recordException(err);
    throw err;
  } finally {
    span.end();
  }
}
```

### Structured Logging

```javascript
const winston = require('winston');

const logger = winston.createLogger({
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.json()
  ),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({ filename: 'gateway.log' })
  ]
});

// Logging middleware
app.use((req, res, next) => {
  const start = Date.now();
  
  res.on('finish', () => {
    logger.info('HTTP Request', {
      method: req.method,
      url: req.url,
      statusCode: res.statusCode,
      duration: Date.now() - start,
      userAgent: req.headers['user-agent'],
      ip: req.ip,
      userId: req.user?.id,
      traceId: req.traceId,
      spanId: req.spanId
    });
  });
  
  next();
});

// Error logging
app.use((err, req, res, next) => {
  logger.error('Request Error', {
    error: err.message,
    stack: err.stack,
    url: req.url,
    method: req.method,
    userId: req.user?.id,
    traceId: req.traceId
  });
  
  next(err);
});
```

---

## Security

### Input Validation

```javascript
const { body, param, query, validationResult } = require('express-validator');

app.post('/api/users',
  // Validation rules
  body('email').isEmail().normalizeEmail(),
  body('age').isInt({ min: 18, max: 120 }),
  body('name').trim().isLength({ min: 2, max: 50 }).escape(),
  
  // Check validation
  (req, res, next) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }
    next();
  },
  
  handleCreateUser
);
```

### SQL Injection Prevention

```javascript
// ❌ BAD: String concatenation
const query = `SELECT * FROM users WHERE id = ${req.params.id}`;

// ✅ GOOD: Parameterized queries
const query = 'SELECT * FROM users WHERE id = ?';
db.query(query, [req.params.id]);
```

### XSS Protection

```javascript
const helmet = require('helmet');

app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'", "'unsafe-inline'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", 'data:', 'https:']
    }
  },
  xssFilter: true
}));
```

### CORS Configuration

```javascript
const cors = require('cors');

app.use(cors({
  origin: (origin, callback) => {
    const allowedOrigins = [
      'https://example.com',
      'https://app.example.com'
    ];
    
    if (!origin || allowedOrigins.includes(origin)) {
      callback(null, true);
    } else {
      callback(new Error('Not allowed by CORS'));
    }
  },
  credentials: true,
  maxAge: 86400 // 24 hours
}));
```

### Request Size Limiting

```javascript
const express = require('express');

app.use(express.json({
  limit: '10mb',
  verify: (req, res, buf) => {
    // Verify payload integrity if needed
  }
}));

app.use(express.urlencoded({
  limit: '10mb',
  extended: true
}));
```

### DDoS Protection

```javascript
const rateLimit = require('express-rate-limit');

// Aggressive rate limit for auth endpoints
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 5, // 5 requests per window
  message: 'Too many login attempts, please try again later'
});

app.post('/api/auth/login', authLimiter, handleLogin);

// Global rate limit
const globalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 100
});

app.use('/api/', globalLimiter);
```

### Security Headers

```javascript
app.use((req, res, next) => {
  // Prevent clickjacking
  res.setHeader('X-Frame-Options', 'DENY');
  
  // Prevent MIME sniffing
  res.setHeader('X-Content-Type-Options', 'nosniff');
  
  // Enable XSS filter
  res.setHeader('X-XSS-Protection', '1; mode=block');
  
  // HSTS
  res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  
  // Remove powered by header
  res.removeHeader('X-Powered-By');
  
  next();
});
```

---

## Technologies & Tools

### 1. Kong

**Type:** Open-source API Gateway and Microservices Management Layer

**Features:**
- Plugin architecture (auth, rate limiting, logging, etc.)
- Admin API and GUI
- Service mesh integration
- Multi-protocol (HTTP, gRPC, WebSocket)

**Configuration:**
```yaml
# kong.yml
services:
  - name: user-service
    url: http://user-service:3000
    routes:
      - name: user-route
        paths:
          - /api/users
    plugins:
      - name: rate-limiting
        config:
          minute: 100
      - name: key-auth
```

**Pros:** Feature-rich, extensible, enterprise support  
**Cons:** Complex setup, Java-based (resource intensive)

### 2. Nginx / OpenResty

**Type:** High-performance web server and reverse proxy

**Configuration:**
```nginx
upstream user_service {
    least_conn;
    server user-1:3000 weight=3;
    server user-2:3000 weight=2;
}

server {
    listen 80;
    
    location /api/users {
        # Rate limiting
        limit_req zone=api_limit burst=20 nodelay;
        
        # Add headers
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # Forward
        proxy_pass http://user_service;
        
        # Caching
        proxy_cache api_cache;
        proxy_cache_valid 200 10m;
    }
}
```

**Pros:** Extremely fast, battle-tested, low resource usage  
**Cons:** Limited dynamic configuration, Lua for advanced features

### 3. AWS API Gateway

**Type:** Fully managed API Gateway service

**Features:**
- Serverless (pay per request)
- Integration with Lambda, AWS services
- Request/response transformation
- Usage plans and API keys
- Swagger/OpenAPI import

**Pros:** No infrastructure management, auto-scaling, AWS integration  
**Cons:** Vendor lock-in, cold start latency, cost at high scale

### 4. Traefik

**Type:** Modern cloud-native reverse proxy and load balancer

**Configuration:**
```yaml
# traefik.yml
http:
  routers:
    user-router:
      rule: "PathPrefix(`/api/users`)"
      service: user-service
      middlewares:
        - auth
        - rate-limit
  
  services:
    user-service:
      loadBalancer:
        servers:
          - url: "http://user-1:3000"
          - url: "http://user-2:3000"
  
  middlewares:
    auth:
      basicAuth:
        users:
          - "user:hashed-password"
    rate-limit:
      rateLimit:
        average: 100
        burst: 50
```

**Pros:** Docker/Kubernetes native, auto-discovery, Let's Encrypt support  
**Cons:** Younger ecosystem, fewer enterprise features

### 5. Envoy Proxy

**Type:** High-performance C++ distributed proxy (service mesh data plane)

**Features:**
- Advanced load balancing
- HTTP/2 and gRPC native
- Observability (metrics, tracing)
- Dynamic configuration

**Configuration:**
```yaml
static_resources:
  listeners:
    - address:
        socket_address:
          address: 0.0.0.0
          port_value: 80
      filter_chains:
        - filters:
            - name: envoy.filters.network.http_connection_manager
              typed_config:
                "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                route_config:
                  virtual_hosts:
                    - name: backend
                      domains: ["*"]
                      routes:
                        - match:
                            prefix: "/api/users"
                          route:
                            cluster: user_service
  
  clusters:
    - name: user_service
      connect_timeout: 0.25s
      type: STRICT_DNS
      lb_policy: ROUND_ROBIN
      load_assignment:
        cluster_name: user_service
        endpoints:
          - lb_endpoints:
              - endpoint:
                  address:
                    socket_address:
                      address: user-service
                      port_value: 3000
```

**Pros:** Extremely performant, service mesh foundation, modern features  
**Cons:** Complex configuration, steep learning curve

### 6. Express Gateway (Node.js)

**Type:** API Gateway built on Express.js

**Configuration:**
```yaml
# gateway.config.yml
apiEndpoints:
  users:
    host: '*'
    paths: '/api/users/*'

serviceEndpoints:
  userService:
    url: 'http://user-service:3000'

policies:
  - basic-auth
  - cors
  - expression
  - key-auth
  - rate-limit
  - proxy

pipelines:
  userPipeline:
    apiEndpoints:
      - users
    policies:
      - key-auth:
      - rate-limit:
          - action:
              max: 100
              windowMs: 60000
      - proxy:
          - action:
              serviceEndpoint: userService
```

**Pros:** JavaScript ecosystem, easy to extend, lightweight  
**Cons:** Node.js performance limits, smaller community

---

## Performance Optimization

### Connection Pooling

```javascript
const axios = require('axios');
const http = require('http');
const https = require('https');

// Create HTTP agent with connection pooling
const httpAgent = new http.Agent({
  keepAlive: true,
  keepAliveMsecs: 30000,
  maxSockets: 50,
  maxFreeSockets: 10,
  timeout: 60000
});

const httpsAgent = new https.Agent({
  keepAlive: true,
  keepAliveMsecs: 30000,
  maxSockets: 50,
  maxFreeSockets: 10,
  timeout: 60000
});

// Use in requests
axios.create({
  httpAgent,
  httpsAgent
});
```

### Request Pipelining

```javascript
// Parallel backend requests when possible
async function getUserDashboard(userId) {
  const [profile, orders, recommendations] = await Promise.all([
    getUserProfile(userId),        // No dependency
    getUserOrders(userId),          // No dependency
    getRecommendations(userId)      // No dependency
  ]);
  
  // Sequential when dependency exists
  const paymentMethods = await getPaymentMethods(profile.accountId);
  
  return { profile, orders, recommendations, paymentMethods };
}
```

### Response Streaming

```javascript
// Stream large responses instead of buffering
app.get('/api/export/large-dataset', async (req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.write('[');
  
  const stream = db.createQueryStream('SELECT * FROM large_table');
  
  let first = true;
  stream.on('data', (row) => {
    if (!first) res.write(',');
    res.write(JSON.stringify(row));
    first = false;
  });
  
  stream.on('end', () => {
    res.write(']');
    res.end();
  });
  
  stream.on('error', (err) => {
    res.status(500).end();
  });
});
```

### Circuit Breaker

```javascript
const CircuitBreaker = require('opossum');

const options = {
  timeout: 3000, // If request takes longer, trigger failure
  errorThresholdPercentage: 50, // Open circuit if 50% fail
  resetTimeout: 30000 // Try again after 30s
};

const breaker = new CircuitBreaker(callBackendService, options);

breaker.fallback(() => ({
  error: 'Service temporarily unavailable',
  cached: getCachedData()
}));

breaker.on('open', () => {
  logger.warn('Circuit breaker opened for backend service');
});

breaker.on('halfOpen', () => {
  logger.info('Circuit breaker half-open, trying request');
});

app.get('/api/data', async (req, res) => {
  try {
    const data = await breaker.fire(req.params);
    res.json(data);
  } catch (err) {
    res.status(503).json({ error: 'Service unavailable' });
  }
});
```

### Compression

```javascript
const compression = require('compression');

app.use(compression({
  level: 6, // Compression level (0-9)
  threshold: 1024, // Only compress if > 1KB
  filter: (req, res) => {
    // Don't compress images, video
    if (req.headers['x-no-compression']) return false;
    return compression.filter(req, res);
  }
}));
```

### Request Timeout

```javascript
const timeout = require('connect-timeout');

// Global timeout
app.use(timeout('30s'));

// Per-route timeout
app.get('/api/slow-endpoint',
  timeout('60s'),
  (req, res) => {
    if (req.timedout) return;
    // Handle request
  }
);

// Handle timeout
app.use((req, res, next) => {
  if (!req.timedout) next();
});
```

---

## Anti-Patterns & Pitfalls

### 1. ❌ Gateway as God Object

**Problem:** Gateway handles too much business logic

```javascript
// ❌ BAD: Business logic in gateway
app.post('/api/orders', async (req, res) => {
  // Validate order
  if (req.body.items.length === 0) {
    return res.status(400).json({ error: 'No items' });
  }
  
  // Calculate total
  let total = 0;
  for (const item of req.body.items) {
    const product = await getProduct(item.productId);
    total += product.price * item.quantity;
    
    // Apply discount
    if (item.quantity > 10) {
      total *= 0.9;
    }
  }
  
  // Check inventory
  for (const item of req.body.items) {
    const stock = await checkInventory(item.productId);
    if (stock < item.quantity) {
      return res.status(400).json({ error: 'Insufficient stock' });
    }
  }
  
  // Create order
  // ... more logic
});

// ✅ GOOD: Gateway routes, backend handles logic
app.post('/api/orders', async (req, res) => {
  const response = await fetch('http://order-service/orders', {
    method: 'POST',
    body: JSON.stringify(req.body),
    headers: {
      'Content-Type': 'application/json',
      'X-User-ID': req.user.id
    }
  });
  
  res.status(response.status).json(await response.json());
});
```

**Solution:** Keep gateway thin - route, authenticate, enforce policies only.

### 2. ❌ Synchronous Cascading Calls

**Problem:** Sequential calls add latency

```javascript
// ❌ BAD: Sequential
const user = await getUserService(userId);
const orders = await getOrderService(userId);
const reviews = await getReviewService(userId);
// Total: 100ms + 150ms + 200ms = 450ms

// ✅ GOOD: Parallel
const [user, orders, reviews] = await Promise.all([
  getUserService(userId),
  getOrderService(userId),
  getReviewService(userId)
]);
// Total: max(100ms, 150ms, 200ms) = 200ms
```

### 3. ❌ No Timeout Configuration

**Problem:** Slow backend hangs gateway

```javascript
// ❌ BAD: No timeout
const response = await fetch('http://slow-service/api');

// ✅ GOOD: With timeout
const controller = new AbortController();
const timeout = setTimeout(() => controller.abort(), 5000);

try {
  const response = await fetch('http://slow-service/api', {
    signal: controller.signal
  });
  clearTimeout(timeout);
} catch (err) {
  if (err.name === 'AbortError') {
    // Handle timeout
  }
}
```

### 4. ❌ Ignoring Partial Failures

**Problem:** One service failure breaks entire request

```javascript
// ❌ BAD: All or nothing
const [user, orders, recommendations] = await Promise.all([
  getUserService(userId),
  getOrderService(userId),
  getRecommendationService(userId) // Fails
]);
// Entire request fails

// ✅ GOOD: Graceful degradation
const results = await Promise.allSettled([
  getUserService(userId),
  getOrderService(userId),
  getRecommendationService(userId)
]);

return {
  user: results[0].status === 'fulfilled' ? results[0].value : null,
  orders: results[1].status === 'fulfilled' ? results[1].value : [],
  recommendations: results[2].status === 'fulfilled' ? results[2].value : []
};
```

### 5. ❌ Caching Authenticated Responses

**Problem:** User A sees User B's data

```javascript
// ❌ BAD: Cache key doesn't include user
const cacheKey = `/api/profile`;
const cached = await cache.get(cacheKey);
// All users get same cached response!

// ✅ GOOD: Include user in cache key
const cacheKey = `/api/profile:${req.user.id}`;
```

### 6. ❌ No Health Check Differentiation

**Problem:** Gateway health check doesn't mean backends are healthy

```javascript
// ❌ BAD: Only gateway health
app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

// ✅ GOOD: Check backend health
app.get('/health', async (req, res) => {
  const checks = await Promise.allSettled([
    checkBackendHealth('user-service'),
    checkBackendHealth('order-service'),
    checkRedisConnection(),
    checkDatabaseConnection()
  ]);
  
  const healthy = checks.every(c => c.status === 'fulfilled' && c.value);
  
  res.status(healthy ? 200 : 503).json({
    status: healthy ? 'healthy' : 'degraded',
    checks: checks.map((c, i) => ({
      service: ['user', 'order', 'redis', 'db'][i],
      status: c.status === 'fulfilled' && c.value ? 'up' : 'down'
    }))
  });
});
```

### 7. ❌ Logging Sensitive Data

**Problem:** Logs expose passwords, tokens, PII

```javascript
// ❌ BAD
logger.info('Request', { body: req.body }); // Logs password!

// ✅ GOOD: Sanitize
function sanitize(obj) {
  const sensitive = ['password', 'token', 'ssn', 'creditCard'];
  const sanitized = { ...obj };
  
  sensitive.forEach(field => {
    if (sanitized[field]) {
      sanitized[field] = '[REDACTED]';
    }
  });
  
  return sanitized;
}

logger.info('Request', { body: sanitize(req.body) });
```

---

## Real-World Examples

### Example 1: E-Commerce API Gateway

```javascript
const express = require('express');
const app = express();

// Middleware stack
app.use(correlationIdMiddleware);
app.use(authenticationMiddleware);
app.use(rateLimitMiddleware);
app.use(loggingMiddleware);

// Product catalog
app.get('/api/products/:id',
  cacheMiddleware(300), // 5 min cache
  async (req, res) => {
    const product = await callService('product-service', `/products/${req.params.id}`);
    res.json(product);
  }
);

// User profile (authenticated, not cached)
app.get('/api/profile',
  requireAuth,
  async (req, res) => {
    const [profile, orders, wishlist] = await Promise.all([
      callService('user-service', `/users/${req.user.id}`),
      callService('order-service', `/orders?userId=${req.user.id}&limit=5`),
      callService('wishlist-service', `/wishlist/${req.user.id}`)
    ]);
    
    res.json({ profile, recentOrders: orders, wishlist });
  }
);

// Place order (authenticated, rate limited)
app.post('/api/orders',
  requireAuth,
  rateLimit({ max: 10, windowMs: 60000 }), // 10 orders/min
  validateOrderMiddleware,
  async (req, res) => {
    const order = await callService('order-service', '/orders', {
      method: 'POST',
      body: {
        ...req.body,
        userId: req.user.id
      }
    });
    
    // Invalidate cache
    await cache.del(`user:${req.user.id}:orders`);
    
    res.status(201).json(order);
  }
);

// Search (cached, rate limited)
app.get('/api/search',
  rateLimit({ max: 100, windowMs: 60000 }),
  cacheMiddleware(60), // 1 min cache
  async (req, res) => {
    const results = await callService('search-service', '/search', {
      query: req.query
    });
    
    res.json(results);
  }
);

app.listen(3000);
```

### Example 2: Multi-Tenant SaaS Gateway

```javascript
// Tenant identification
app.use((req, res, next) => {
  // Extract tenant from subdomain or header
  const subdomain = req.hostname.split('.')[0];
  const tenantHeader = req.headers['x-tenant-id'];
  
  req.tenantId = tenantHeader || subdomain;
  
  // Validate tenant
  if (!isValidTenant(req.tenantId)) {
    return res.status(404).json({ error: 'Tenant not found' });
  }
  
  next();
});

// Tenant-specific rate limiting
app.use(async (req, res, next) => {
  const tenant = await getTenantConfig(req.tenantId);
  const limit = tenant.tier === 'premium' ? 10000 : 1000;
  
  const key = `rate:${req.tenantId}`;
  const current = await redis.incr(key);
  
  if (current === 1) {
    await redis.expire(key, 3600); // 1 hour
  }
  
  if (current > limit) {
    return res.status(429).json({
      error: 'Rate limit exceeded',
      tier: tenant.tier,
      limit
    });
  }
  
  next();
});

// Tenant-specific routing
app.use((req, res, next) => {
  // Route to tenant-specific shard
  const shard = getTenantShard(req.tenantId);
  req.backendUrl = `http://api-${shard}.internal`;
  next();
});

// Forward with tenant context
app.all('/api/*', async (req, res) => {
  const response = await fetch(`${req.backendUrl}${req.path}`, {
    method: req.method,
    headers: {
      ...req.headers,
      'X-Tenant-ID': req.tenantId,
      'X-Tenant-Tier': (await getTenantConfig(req.tenantId)).tier
    },
    body: req.method !== 'GET' ? JSON.stringify(req.body) : undefined
  });
  
  res.status(response.status).json(await response.json());
});
```

### Example 3: Mobile Backend for Frontend

```javascript
// Optimized for mobile clients
app.get('/api/mobile/home',
  requireAuth,
  cacheMiddleware(60),
  async (req, res) => {
    // Parallel requests
    const [user, feed, notifications, promotions] = await Promise.allSettled([
      callService('user-service', `/users/${req.user.id}`),
      callService('feed-service', `/feed/${req.user.id}?limit=20`),
      callService('notification-service', `/notifications/${req.user.id}?unread=true`),
      callService('promotion-service', '/promotions/active')
    ]);
    
    // Optimize payload size
    const response = {
      user: user.status === 'fulfilled' ? {
        id: user.value.id,
        name: user.value.name,
        avatar: user.value.avatar,
        // Exclude unnecessary fields
      } : null,
      
      feed: feed.status === 'fulfilled' ? feed.value.items.map(item => ({
        id: item.id,
        title: item.title,
        thumbnail: item.thumbnail, // Low-res for mobile
        timestamp: item.timestamp
        // Exclude body, full image
      })) : [],
      
      unreadCount: notifications.status === 'fulfilled' ? 
        notifications.value.length : 0,
      
      promotion: promotions.status === 'fulfilled' && promotions.value.length > 0 ?
        promotions.value[0] : null
    };
    
    // Compress response
    res.json(response);
  }
);

// Image proxy with resizing
app.get('/api/mobile/images/:imageId',
  async (req, res) => {
    const size = req.query.size || 'medium'; // small, medium, large
    
    const cacheKey = `image:${req.params.imageId}:${size}`;
    const cached = await cache.get(cacheKey);
    
    if (cached) {
      res.set('Content-Type', 'image/jpeg');
      res.set('Cache-Control', 'public, max-age=86400');
      return res.send(Buffer.from(cached, 'base64'));
    }
    
    // Fetch and resize
    const image = await callService('image-service', `/images/${req.params.imageId}`);
    const resized = await resizeImage(image, size);
    
    await cache.setex(cacheKey, 86400, resized.toString('base64'));
    
    res.set('Content-Type', 'image/jpeg');
    res.set('Cache-Control', 'public, max-age=86400');
    res.send(resized);
  }
);
```

---

## Summary

### Key Takeaways

1. **Single Entry Point**: Gateway provides unified interface for microservices
2. **Cross-Cutting Concerns**: Centralize authentication, rate limiting, logging
3. **Protocol Translation**: Bridge different protocols (HTTP, gRPC, WebSocket)
4. **Performance**: Use caching, connection pooling, parallel requests
5. **Resilience**: Implement circuit breakers, timeouts, graceful degradation
6. **Security**: Validate input, enforce authentication, prevent abuse
7. **Observability**: Comprehensive metrics, logging, distributed tracing
8. **Keep It Thin**: Gateway should route and enforce policies, not business logic

### Best Practices Checklist

✅ Implement authentication and authorization  
✅ Add rate limiting to prevent abuse  
✅ Cache responses where appropriate  
✅ Use connection pooling for backend calls  
✅ Implement circuit breakers for resilience  
✅ Add comprehensive logging and metrics  
✅ Handle partial failures gracefully  
✅ Set timeouts on all backend calls  
✅ Validate and sanitize all inputs  
✅ Use distributed tracing for debugging  
✅ Monitor gateway and backend health  
✅ Document API contracts and SLAs  

### Common Use Cases

- **Microservices Architecture**: Single entry for multiple services
- **Mobile/Web APIs**: Backend for Frontend pattern
- **Third-Party API Management**: Control and monetize API access
- **Legacy System Integration**: Modernize with gateway facade
- **Multi-Tenant SaaS**: Tenant isolation and routing
- **API Versioning**: Support multiple API versions
- **Security Gateway**: Authentication, authorization, DDoS protection
