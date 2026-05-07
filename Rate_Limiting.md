# Rate Limiting: A Complete Technical Guide

## Table of Contents
1. [Fundamentals](#fundamentals)
2. [Rate Limiting Algorithms](#rate-limiting-algorithms)
3. [Implementation Patterns](#implementation-patterns)
4. [Distributed Rate Limiting](#distributed-rate-limiting)
5. [Rate Limiting Strategies](#rate-limiting-strategies)
6. [Storage & State Management](#storage--state-management)
7. [Response Handling](#response-handling)
8. [Technologies & Tools](#technologies--tools)
9. [Advanced Concepts](#advanced-concepts)
10. [Anti-Patterns & Pitfalls](#anti-patterns--pitfalls)
11. [Real-World Examples](#real-world-examples)

---

## Fundamentals

### What is Rate Limiting?

Rate limiting is a technique to control the rate at which users or services can access an API or resource. It restricts the number of requests a client can make within a specified time window, protecting systems from abuse, ensuring fair resource allocation, and maintaining service quality.

**Key Metrics:**
- **Rate**: Number of allowed requests per time window (e.g., 100 requests/minute)
- **Quota**: Total resource allocation over a longer period (e.g., 10,000 requests/day)
- **Burst**: Maximum requests allowed in a short time span
- **Throttling**: Action taken when limit is exceeded (reject, queue, slow down)

### Why Rate Limiting?

1. **Prevent Resource Exhaustion**: Protect servers from overload
2. **Cost Control**: Limit expensive operations (API calls, compute)
3. **Security**: Mitigate DDoS attacks, brute force attempts
4. **Fair Usage**: Ensure equitable access for all users
5. **Service Quality**: Maintain performance for legitimate users
6. **Revenue Protection**: Enforce API tier limits for monetization
7. **Prevent Abuse**: Stop scraping, automated bots, spam

### When to Apply Rate Limiting

✅ **Essential For:**
- Public APIs and endpoints
- Authentication endpoints (login, password reset)
- Resource-intensive operations (search, file uploads)
- Third-party API wrappers
- Payment processing endpoints
- Content creation/modification actions
- Email/SMS sending services

❌ **Consider Carefully:**
- Internal services (use circuit breakers instead)
- Health check endpoints
- Static asset serving (use CDN instead)
- Real-time collaborative features

---

## Rate Limiting Algorithms

### 1. Token Bucket

**Concept**: A bucket holds tokens. Each request consumes a token. Tokens are added at a fixed rate. When bucket is empty, requests are rejected.

**Parameters:**
- Bucket capacity (max tokens)
- Refill rate (tokens per second)

```
tokens = min(capacity, tokens + (now - last_refill) * rate)
if tokens >= 1:
    tokens -= 1
    allow request
else:
    reject request
```

**Characteristics:**
- ✅ Allows bursts up to bucket capacity
- ✅ Smooth token refill
- ✅ Memory efficient (stores token count + timestamp)
- ❌ Burst can exhaust resources quickly
- ❌ Complex to explain to users

**Use Cases:**
- Network traffic shaping
- APIs allowing occasional bursts
- AWS API Gateway default algorithm

### 2. Leaky Bucket

**Concept**: Requests enter a bucket queue. Requests are processed at a fixed rate. If bucket overflows, requests are rejected.

```
if queue.size < capacity:
    queue.add(request)
else:
    reject request

// Separate process
every fixed_interval:
    process queue.dequeue()
```

**Characteristics:**
- ✅ Smooths out traffic spikes
- ✅ Predictable output rate
- ✅ Good for rate-sensitive backends
- ❌ No burst allowance
- ❌ Requires queue storage
- ❌ Adds latency (queueing delay)

**Use Cases:**
- Network routers
- Message queue processors
- Background job systems

### 3. Fixed Window Counter

**Concept**: Count requests in fixed time windows (e.g., per minute). Reset counter at window boundary.

```
window = floor(now / window_size)
key = user_id + ":" + window
counter = increment(key)
if counter <= limit:
    allow request
else:
    reject request
```

**Characteristics:**
- ✅ Simple to implement
- ✅ Memory efficient
- ✅ Easy to explain
- ❌ Boundary problem: 2x burst at window edges
- ❌ Unfair: early requests in window get priority

**Boundary Issue Example:**
```
Limit: 10 requests/minute
Window 1: 12:00:00 - 12:00:59 → 10 requests at 12:00:59
Window 2: 12:01:00 - 12:01:59 → 10 requests at 12:01:00
Result: 20 requests in 2 seconds!
```

**Use Cases:**
- Analytics counting
- Simple API quotas
- Non-critical rate limiting

### 4. Sliding Window Log

**Concept**: Store timestamp of each request. Count requests in the sliding window by filtering timestamps.

```
timestamps = get_user_timestamps(user_id)
current_window = now - window_size
valid_timestamps = filter(timestamps, t => t > current_window)

if valid_timestamps.count <= limit:
    valid_timestamps.add(now)
    allow request
else:
    reject request
```

**Characteristics:**
- ✅ Accurate: no boundary problem
- ✅ Precise enforcement
- ❌ Memory intensive (store all timestamps)
- ❌ Performance overhead (filtering)
- ❌ Scales poorly with high limits

**Use Cases:**
- Premium/paid API tiers
- Financial transactions
- Critical security endpoints

### 5. Sliding Window Counter

**Concept**: Hybrid of fixed window and sliding window. Use weighted count from previous and current windows.

```
previous_window_count = get_count(previous_window_key)
current_window_count = get_count(current_window_key)

elapsed_time = now % window_size
weight = (window_size - elapsed_time) / window_size

estimated_count = (previous_window_count * weight) + current_window_count

if estimated_count <= limit:
    increment(current_window_key)
    allow request
else:
    reject request
```

**Characteristics:**
- ✅ Memory efficient (2 counters)
- ✅ Smooths boundary issues
- ✅ Good approximation of sliding window
- ❌ Not 100% accurate (uses estimation)
- ❌ More complex than fixed window

**Use Cases:**
- High-traffic APIs
- Production systems (good balance)
- Stripe, Cloudflare use this

### Algorithm Comparison Table

| Algorithm | Accuracy | Memory | Burst | Implementation | Use Case |
|-----------|----------|--------|-------|----------------|----------|
| Token Bucket | Good | Low | Yes | Moderate | General APIs |
| Leaky Bucket | Perfect | High | No | Complex | Traffic shaping |
| Fixed Window | Low | Very Low | Yes (edge) | Simple | Basic quotas |
| Sliding Log | Perfect | Very High | No | Moderate | Critical APIs |
| Sliding Counter | Good | Low | Minimal | Moderate | Production (recommended) |

---

## Implementation Patterns

### Single Server Implementation

**In-Memory (Application Level)**

```python
# Python with dict
class RateLimiter:
    def __init__(self):
        self.requests = {}  # {user_id: [timestamps]}
    
    def allow_request(self, user_id, limit=100, window=60):
        now = time.time()
        if user_id not in self.requests:
            self.requests[user_id] = []
        
        # Remove old timestamps
        cutoff = now - window
        self.requests[user_id] = [t for t in self.requests[user_id] if t > cutoff]
        
        if len(self.requests[user_id]) < limit:
            self.requests[user_id].append(now)
            return True
        return False
```

**Advantages:**
- Fast (in-process memory)
- Simple implementation
- No network calls

**Limitations:**
- Lost on restart
- Doesn't work across multiple servers
- Memory grows with users

### Distributed Implementation

**Redis-Based (Token Bucket)**

```python
import redis
import time

def allow_request(redis_client, user_id, rate=10, capacity=10):
    key = f"rate_limit:{user_id}"
    now = time.time()
    
    # Lua script for atomic operation
    script = """
    local key = KEYS[1]
    local capacity = tonumber(ARGV[1])
    local rate = tonumber(ARGV[2])
    local now = tonumber(ARGV[3])
    
    local tokens_key = key .. ":tokens"
    local timestamp_key = key .. ":timestamp"
    
    local tokens = tonumber(redis.call('get', tokens_key))
    local last_refill = tonumber(redis.call('get', timestamp_key))
    
    if tokens == nil then
        tokens = capacity
        last_refill = now
    end
    
    -- Refill tokens
    local elapsed = now - last_refill
    local new_tokens = math.min(capacity, tokens + elapsed * rate)
    
    if new_tokens >= 1 then
        new_tokens = new_tokens - 1
        redis.call('set', tokens_key, new_tokens)
        redis.call('set', timestamp_key, now)
        return 1  -- Allow
    else
        return 0  -- Reject
    end
    """
    
    result = redis_client.eval(script, 1, key, capacity, rate, now)
    return result == 1
```

**Redis Sliding Window Counter**

```python
def sliding_window_limiter(redis_client, user_id, limit=100, window=60):
    now = time.time()
    current_window = int(now / window)
    previous_window = current_window - 1
    
    current_key = f"rate_limit:{user_id}:{current_window}"
    previous_key = f"rate_limit:{user_id}:{previous_window}"
    
    # Get counts
    current_count = int(redis_client.get(current_key) or 0)
    previous_count = int(redis_client.get(previous_key) or 0)
    
    # Calculate weighted count
    elapsed = now % window
    weight = (window - elapsed) / window
    estimated = (previous_count * weight) + current_count
    
    if estimated < limit:
        pipe = redis_client.pipeline()
        pipe.incr(current_key)
        pipe.expire(current_key, window * 2)  # Keep for 2 windows
        pipe.execute()
        return True
    return False
```

### Middleware Pattern

**Express.js Example**

```javascript
const rateLimit = require('express-rate-limit');

const limiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 100, // Limit each IP to 100 requests per windowMs
    message: 'Too many requests, please try again later.',
    standardHeaders: true, // Return rate limit info in headers
    legacyHeaders: false,
});

app.use('/api/', limiter);
```

**Custom Middleware**

```javascript
async function rateLimitMiddleware(req, res, next) {
    const identifier = req.ip || req.user?.id;
    const key = `rate_limit:${identifier}`;
    
    const allowed = await rateLimiter.checkLimit(key);
    
    if (allowed) {
        const remaining = await rateLimiter.getRemaining(key);
        res.setHeader('X-RateLimit-Remaining', remaining);
        next();
    } else {
        const resetTime = await rateLimiter.getResetTime(key);
        res.setHeader('X-RateLimit-Reset', resetTime);
        res.status(429).json({
            error: 'Rate limit exceeded',
            retryAfter: resetTime - Date.now()
        });
    }
}
```

---

## Distributed Rate Limiting

### Challenges in Distributed Systems

1. **Race Conditions**: Multiple servers incrementing counters simultaneously
2. **Clock Synchronization**: Servers may have time drift
3. **Network Latency**: Delay in checking/updating shared state
4. **Consistency**: CAP theorem tradeoffs
5. **Performance**: Network calls add overhead

### Solutions

#### 1. Centralized Counter (Redis)

**Approach**: All servers check/update Redis

```
Client → Server A ──┐
Client → Server B ──┼──→ Redis (rate limit state)
Client → Server C ──┘
```

**Pros:**
- Accurate counting
- Easy to implement
- Single source of truth

**Cons:**
- Redis becomes bottleneck
- Single point of failure
- Network latency on every request

**Optimization**: Use Redis pipelining and Lua scripts for atomic operations

#### 2. Local Counter + Periodic Sync

**Approach**: Each server has local counter, periodically syncs with Redis

```
Server A (local: 45) ──┐
Server B (local: 32) ──┼──→ Redis (total: 150)
Server C (local: 73) ──┘
    ↓ sync every 1s
```

**Pros:**
- Fast (mostly local)
- Reduced Redis load

**Cons:**
- Can exceed limit temporarily
- Complex synchronization

#### 3. Distributed Counter (Gossip Protocol)

**Approach**: Servers share counter updates via peer-to-peer gossip

**Pros:**
- No central point of failure
- Scalable

**Cons:**
- Eventually consistent
- Complex implementation
- Higher network overhead

#### 4. Sticky Sessions

**Approach**: Route same user to same server (load balancer)

**Pros:**
- Simple per-server rate limiting works
- No distributed coordination

**Cons:**
- Uneven load distribution
- Fails on server restart
- Doesn't truly limit across cluster

### Recommended Patterns

**For Production:**
1. **Redis with Lua scripts** - Best balance of accuracy and performance
2. **Sliding Window Counter** - Memory efficient, accurate enough
3. **Regional Redis clusters** - Reduce latency with geo-distribution
4. **Cache-aside pattern** - Local cache with Redis fallback

---

## Rate Limiting Strategies

### 1. By User/Account

```
Identifier: User ID, Account ID
Use Case: SaaS platforms, authenticated APIs
Example: Each user gets 1000 requests/hour
```

### 2. By IP Address

```
Identifier: Client IP
Use Case: Public endpoints, anonymous access
Example: Each IP gets 100 requests/minute
Challenge: Shared IPs (NAT, corporate networks)
```

### 3. By API Key/Token

```
Identifier: API key
Use Case: Third-party integrations, developer APIs
Example: Free tier: 1000/day, Pro: 100,000/day
```

### 4. By Endpoint/Resource

```
Identifier: URL path or resource type
Use Case: Protecting expensive operations
Example: /search: 10/min, /profile: 100/min
```

### 5. By Geographic Region

```
Identifier: Country, region
Use Case: Compliance, service tiers
Example: US: 1000/min, Other: 500/min
```

### 6. Hierarchical/Composite

```
Multiple levels:
- Global: 1M requests/day across all users
- Per Account: 10K requests/day
- Per User: 1K requests/day
- Per IP: 100 requests/minute

Check all levels, enforce strictest limit
```

### 7. Adaptive/Dynamic

```
Adjust limits based on:
- System load
- Time of day
- User behavior
- Historical patterns

Example: Reduce limits during peak hours
```

---

## Storage & State Management

### Storage Options

#### 1. Redis

**Pros:**
- Fast (in-memory)
- Atomic operations
- Built-in expiration (TTL)
- Lua scripting
- Persistence options

**Cons:**
- Cost at scale
- Memory limits
- Single point of failure (needs clustering)

**Best Practices:**
```
- Use Redis Cluster for high availability
- Set TTL on keys to prevent memory leaks
- Use Lua scripts for atomic operations
- Monitor memory usage
- Use pipelining for batching
```

#### 2. Memcached

**Pros:**
- Fast
- Simple
- Multi-threaded

**Cons:**
- No persistence
- No atomic operations
- Limited data structures

**Use Case**: Simple counters, non-critical limits

#### 3. Database (PostgreSQL, MySQL)

**Pros:**
- Persistent
- ACID compliance
- Complex queries

**Cons:**
- Slower than Redis
- Can become bottleneck
- Disk I/O overhead

**Use Case**: Quota tracking, billing, analytics

#### 4. In-Memory (Application)

**Pros:**
- Fastest
- No network overhead
- Simple

**Cons:**
- Lost on restart
- Doesn't work distributed
- Memory per server

**Use Case**: Single server, development

### State Management Patterns

#### 1. Time-Based Keys

```
key = f"rate_limit:{user_id}:{timestamp}"

# Fixed window
window = int(now / 60)  # 1-minute window
key = f"rl:{user_id}:{window}"

# Expiration
redis.setex(key, 120, counter)  # Expire after 2 windows
```

#### 2. Sorted Sets (Sliding Window Log)

```
# Add timestamp
redis.zadd(f"rl:{user_id}", {now: now})

# Remove old entries
cutoff = now - window
redis.zremrangebyscore(f"rl:{user_id}", 0, cutoff)

# Count
count = redis.zcard(f"rl:{user_id}")
```

#### 3. Hash Map (Token Bucket)

```
redis.hset(f"rl:{user_id}", mapping={
    "tokens": tokens,
    "last_refill": now
})
```

---

## Response Handling

### HTTP Status Codes

- **429 Too Many Requests**: Standard rate limit exceeded
- **503 Service Unavailable**: System overload, try later
- **420 Enhance Your Calm**: Twitter's custom code (deprecated)

### Response Headers (RFC 6585)

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1683547200
Retry-After: 60

{
    "error": {
        "code": "rate_limit_exceeded",
        "message": "Rate limit exceeded. Try again in 60 seconds.",
        "limit": 100,
        "remaining": 0,
        "reset": 1683547200
    }
}
```

### Standard Headers

```
X-RateLimit-Limit: Maximum requests in window
X-RateLimit-Remaining: Requests remaining in current window
X-RateLimit-Reset: Unix timestamp when limit resets
Retry-After: Seconds until retry (or HTTP date)
```

### User Experience Best Practices

1. **Clear Error Messages**: Explain why request was rejected
2. **Include Reset Time**: Tell when they can retry
3. **Show Remaining Quota**: Let users pace themselves
4. **Provide Upgrade Path**: Link to higher tier plans
5. **Log for Analysis**: Track who hits limits frequently

### Client-Side Handling

```javascript
async function apiCallWithRetry(url, options, maxRetries = 3) {
    for (let i = 0; i < maxRetries; i++) {
        const response = await fetch(url, options);
        
        if (response.status === 429) {
            const retryAfter = response.headers.get('Retry-After');
            const waitTime = retryAfter ? parseInt(retryAfter) * 1000 : (2 ** i) * 1000;
            
            console.log(`Rate limited. Waiting ${waitTime}ms before retry...`);
            await sleep(waitTime);
            continue;
        }
        
        return response;
    }
    
    throw new Error('Max retries exceeded');
}
```

---

## Technologies & Tools

### Rate Limiting Services

#### 1. **Redis**
```bash
# Install
brew install redis

# Basic usage
redis-cli
> INCR rate_limit:user123
> EXPIRE rate_limit:user123 60
```

#### 2. **Nginx**

```nginx
# Limit requests per second
limit_req_zone $binary_remote_addr zone=mylimit:10m rate=10r/s;

server {
    location /api/ {
        limit_req zone=mylimit burst=20 nodelay;
        proxy_pass http://backend;
    }
}
```

#### 3. **Kong API Gateway**

```yaml
plugins:
  - name: rate-limiting
    config:
      minute: 100
      hour: 10000
      policy: redis
      redis_host: localhost
```

#### 4. **AWS API Gateway**

```
- Usage Plans: Define rate and burst limits
- API Keys: Associate keys with usage plans
- Throttling: 10,000 requests/sec default (AWS account level)
```

#### 5. **Cloudflare**

```
- Rate Limiting Rules: Configure via dashboard
- Edge-based: Applies before reaching origin
- Geo-based: Different limits per region
```

### Libraries

#### Node.js
```javascript
// express-rate-limit
const rateLimit = require('express-rate-limit');

// rate-limiter-flexible (Redis)
const { RateLimiterRedis } = require('rate-limiter-flexible');
```

#### Python
```python
# Flask-Limiter
from flask_limiter import Limiter

# django-ratelimit
from django_ratelimit.decorators import ratelimit
```

#### Go
```go
// golang.org/x/time/rate
import "golang.org/x/time/rate"
limiter := rate.NewLimiter(10, 1) // 10 requests/sec, burst 1

// tollbooth
import "github.com/didip/tollbooth/v6"
```

#### Java
```java
// Guava RateLimiter
RateLimiter limiter = RateLimiter.create(10.0); // 10 permits/sec
limiter.acquire(); // May wait

// Bucket4j
Bucket bucket = Bucket4j.builder()
    .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
    .build();
```

---

## Advanced Concepts

### 1. Penalty Box / Temporary Ban

**Concept**: Users who repeatedly hit limits get temporarily banned

```python
def check_penalty_box(user_id):
    key = f"penalty:{user_id}"
    penalty_count = redis.get(key) or 0
    
    if penalty_count > 10:  # Hit limit 10 times
        redis.setex(f"banned:{user_id}", 3600, 1)  # Ban for 1 hour
        return False
    return True

def rate_limit_with_penalty(user_id):
    if not check_penalty_box(user_id):
        return False  # Banned
    
    if not allow_request(user_id):
        redis.incr(f"penalty:{user_id}")
        redis.expire(f"penalty:{user_id}", 300)  # 5 min window
        return False
    return True
```

### 2. Request Cost / Weighted Limits

**Concept**: Different operations consume different amounts of quota

```python
OPERATION_COSTS = {
    'read': 1,
    'write': 5,
    'search': 10,
    'export': 50
}

def check_limit_with_cost(user_id, operation):
    cost = OPERATION_COSTS[operation]
    current = redis.get(f"quota:{user_id}") or 0
    
    if current + cost <= DAILY_QUOTA:
        redis.incrby(f"quota:{user_id}", cost)
        return True
    return False
```

### 3. Token Economy / Credits

**Concept**: Users earn tokens over time or through actions

```python
def award_tokens(user_id, amount):
    redis.incrby(f"tokens:{user_id}", amount)

def consume_tokens(user_id, cost):
    tokens = int(redis.get(f"tokens:{user_id}") or 0)
    if tokens >= cost:
        redis.decrby(f"tokens:{user_id}", cost)
        return True
    return False
```

### 4. Quota Carryover

**Concept**: Unused quota from previous period carries over

```python
def check_with_carryover(user_id, limit=1000):
    current_period = get_current_period()
    prev_period = current_period - 1
    
    prev_used = get_usage(user_id, prev_period)
    prev_unused = max(0, limit - prev_used)
    
    current_limit = limit + min(prev_unused, limit * 0.5)  # Carry 50% max
    current_used = get_usage(user_id, current_period)
    
    return current_used < current_limit
```

### 5. Circuit Breaker Integration

**Concept**: Rate limiter cooperates with circuit breaker

```python
def request_with_circuit_breaker(service):
    if circuit_breaker.is_open():
        return None  # Service unavailable
    
    if not rate_limiter.allow():
        circuit_breaker.record_failure()
        return None
    
    response = call_service(service)
    if response.success:
        circuit_breaker.record_success()
    else:
        circuit_breaker.record_failure()
    
    return response
```

### 6. Priority Queuing

**Concept**: Premium users get priority during congestion

```python
def request_with_priority(user_id, priority='normal'):
    if priority == 'premium':
        return allow_request(user_id, limit=10000)
    elif priority == 'normal':
        return allow_request(user_id, limit=1000)
    else:
        return allow_request(user_id, limit=100)
```

### 7. Predictive Rate Limiting

**Concept**: Use ML to predict abusive patterns

```python
def smart_rate_limit(user_id, request_pattern):
    risk_score = ml_model.predict(request_pattern)
    
    if risk_score > 0.8:  # High risk
        return allow_request(user_id, limit=10)  # Strict
    elif risk_score > 0.5:  # Medium risk
        return allow_request(user_id, limit=100)
    else:  # Low risk
        return allow_request(user_id, limit=1000)
```

---

## Anti-Patterns & Pitfalls

### 1. ❌ Not Using Atomic Operations

**Problem**: Race conditions in distributed systems

```python
# BAD
count = redis.get(key)
if count < limit:
    redis.incr(key)  # Race condition!
```

**Solution**: Use Lua scripts or Redis transactions

```python
# GOOD
script = """
local count = redis.call('incr', KEYS[1])
if count > tonumber(ARGV[1]) then
    redis.call('decr', KEYS[1])
    return 0
end
return 1
"""
result = redis.eval(script, 1, key, limit)
```

### 2. ❌ No Key Expiration

**Problem**: Memory leaks, stale data

```python
# BAD
redis.incr(f"rate_limit:{user_id}")  # Never expires!

# GOOD
redis.incr(f"rate_limit:{user_id}")
redis.expire(f"rate_limit:{user_id}", 3600)
```

### 3. ❌ Limiting Healthy Traffic

**Problem**: Rate limiting legitimate batch operations

```python
# BAD: Same limit for all operations
/api/items  # Rate limited at 100/hour

# GOOD: Different limits per operation type
/api/items (read)  # 1000/hour
/api/items (write) # 100/hour
/api/items/bulk    # 10/hour
```

### 4. ❌ No Graceful Degradation

**Problem**: Hard failures when Redis is down

```python
# BAD
if not redis.ping():
    raise Exception("Rate limiter unavailable")

# GOOD
try:
    if not rate_limiter.check():
        return 429
except RedisConnectionError:
    logger.warning("Redis down, allowing request")
    return process_request()  # Fail open
```

### 5. ❌ Inconsistent Identifiers

**Problem**: Users bypass limits by switching IPs/keys

```python
# BAD: Only IP-based
key = request.ip

# GOOD: Multi-factor
key = f"{request.ip}:{request.user_agent}:{request.user_id}"
# Or use fingerprinting
```

### 6. ❌ Not Monitoring

**Problem**: Can't detect attacks or adjust limits

**Solution**: Log and alert on:
- High rate limit hit frequency
- Unusual traffic patterns
- Top offenders
- Regional anomalies

### 7. ❌ Shared Global Limit

**Problem**: One user exhausts quota for all

```python
# BAD
if total_requests > 1000000:  # Global limit
    reject_all()

# GOOD: Per-user limits with global soft limit
if user_requests > 10000:
    reject_user()
if total_requests > 1000000:
    reduce_all_limits(0.5)  # Graceful degradation
```

### 8. ❌ Synchronous Rate Limiting

**Problem**: Adding latency to every request

```python
# BAD
response = check_redis_rate_limit()  # Network call
if response.allowed:
    process_request()

# BETTER: Async check
async def handle_request():
    allowed = await check_rate_limit()  # Non-blocking
    if allowed:
        return await process_request()
```

---

## Real-World Examples

### 1. **GitHub API**

```
Authenticated: 5,000 requests/hour
Unauthenticated: 60 requests/hour

Headers:
X-RateLimit-Limit: 5000
X-RateLimit-Remaining: 4999
X-RateLimit-Reset: 1372700873
X-RateLimit-Used: 1
X-RateLimit-Resource: core
```

**Algorithm**: Sliding window counter

### 2. **Twitter API**

```
Tweet posting: 300 tweets/3 hours
Following: 400 follows/day
Searching: 180 requests/15 minutes (user auth)
           450 requests/15 minutes (app auth)
```

**Algorithm**: Fixed window with user-based and app-based limits

### 3. **Stripe API**

```
Test mode: 25 requests/second
Live mode: 100 requests/second (with burst)

No hard daily/monthly limits
Read operations: More lenient
Write operations: Stricter
```

**Algorithm**: Token bucket (allows bursts)

### 4. **AWS API Gateway**

```
Default: 10,000 requests/second per account per region
Burst: 5,000 requests
Configurable per API/stage
```

**Algorithm**: Token bucket

### 5. **Google Maps API**

```
Free tier: 
- Maps: 28,000 requests/month
- Geocoding: 40,000 requests/month

Rate: Configurable (default 50 QPS)
```

**Algorithm**: Quota (monthly) + Rate limit (per second)

### 6. **Cloudflare**

```
Free: 1,000 requests/second per zone
Enterprise: Configurable

DDoS Protection: Automatic rate limiting
Based on: IP reputation, traffic patterns, ML
```

**Algorithm**: Adaptive + behavioral analysis

### 7. **Reddit API**

```
OAuth: 60 requests/minute
Without OAuth: 10 requests/minute

Respects HTTP 429 and Retry-After
```

**Algorithm**: Fixed window

### 8. **Discord API**

```
Global: 50 requests/second
Per-route: Varies (e.g., 5/second for messages)

Headers:
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1470173023
X-RateLimit-Bucket: abcd1234
```

**Algorithm**: Token bucket with per-route limits

---

## System Design Interview Tips

### Key Discussion Points

1. **Requirements Clarification:**
   - Who are we rate limiting? (User, IP, API key)
   - What are the limits? (requests/second, minute, hour, day)
   - What happens when exceeded? (reject, queue, slow down)
   - Single server or distributed?
   - Accuracy requirements?

2. **Algorithm Selection:**
   - Start with sliding window counter (good balance)
   - Justify based on requirements
   - Discuss tradeoffs

3. **Scalability:**
   - Redis cluster for state management
   - Regional distribution
   - Handle Redis failures (circuit breaker)

4. **Monitoring & Observability:**
   - Metrics: hit rate, rejection rate, latency
   - Alerts: abnormal patterns
   - Dashboards: per-user, per-endpoint analytics

5. **Edge Cases:**
   - Clock skew between servers
   - Redis connection failures
   - Bursty traffic patterns
   - Shared IPs (NAT)

### Sample Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ▼
┌─────────────────────────┐
│   Load Balancer         │
│   (Nginx/HAProxy)       │
└──────┬──────────────────┘
       │
       ├──────────┬─────────────┐
       ▼          ▼             ▼
┌──────────┐ ┌──────────┐ ┌──────────┐
│  Server  │ │  Server  │ │  Server  │
│    1     │ │    2     │ │    3     │
└────┬─────┘ └────┬─────┘ └────┬─────┘
     │            │            │
     └────────────┼────────────┘
                  │
                  ▼
         ┌─────────────────┐
         │  Redis Cluster  │
         │  (Rate Limiter  │
         │   State)        │
         └─────────────────┘
```

### Code Template for Interview

```python
class RateLimiter:
    def __init__(self, redis_client):
        self.redis = redis_client
    
    def is_allowed(self, user_id, limit=100, window=60):
        """
        Sliding window counter algorithm
        
        Args:
            user_id: Identifier for the user
            limit: Max requests in window
            window: Time window in seconds
        
        Returns:
            bool: True if request allowed, False otherwise
        """
        now = time.time()
        current_window = int(now / window)
        previous_window = current_window - 1
        
        current_key = f"rl:{user_id}:{current_window}"
        previous_key = f"rl:{user_id}:{previous_window}"
        
        # Get counts atomically using Lua
        script = """
        local curr = tonumber(redis.call('get', KEYS[1]) or "0")
        local prev = tonumber(redis.call('get', KEYS[2]) or "0")
        local limit = tonumber(ARGV[1])
        local window = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        local weight = (window - (now % window)) / window
        local count = (prev * weight) + curr
        
        if count < limit then
            redis.call('incr', KEYS[1])
            redis.call('expire', KEYS[1], window * 2)
            return 1
        end
        return 0
        """
        
        result = self.redis.eval(
            script, 
            2, 
            current_key, 
            previous_key, 
            limit, 
            window, 
            now
        )
        
        return result == 1
```

---

## Summary Cheat Sheet

### When to Use What

| Scenario | Algorithm | Storage | Strategy |
|----------|-----------|---------|----------|
| High-traffic API | Sliding Window Counter | Redis Cluster | Per API key |
| Login endpoint | Token Bucket | Redis | Per IP + User |
| Public website | Fixed Window | Redis/Nginx | Per IP |
| Premium API | Sliding Window Log | Redis | Per tier |
| Internal service | Token Bucket | In-memory | Per service |
| Webhook delivery | Leaky Bucket | Queue | Per webhook |
| Search endpoint | Token Bucket | Redis | Per user |

### Quick Decision Tree

```
Is it distributed?
├─ Yes → Use Redis
│   └─ High accuracy needed?
│       ├─ Yes → Sliding Window Log
│       └─ No → Sliding Window Counter (recommended)
│
└─ No → Use in-memory
    └─ Need bursts?
        ├─ Yes → Token Bucket
        └─ No → Fixed Window
```

### Key Metrics to Monitor

1. **Rate limit hit rate**: % of requests hitting limit
2. **False positive rate**: Legitimate users blocked
3. **Latency overhead**: Time spent on rate limiting
4. **Storage usage**: Redis memory consumption
5. **Top offenders**: Users frequently hitting limits
6. **Bypass attempts**: Pattern of limit circumvention

---

## References & Further Reading

- [GCRA: Generic Cell Rate Algorithm](https://en.wikipedia.org/wiki/Generic_cell_rate_algorithm)
- [RFC 6585: Additional HTTP Status Codes](https://tools.ietf.org/html/rfc6585)
- [Redis Rate Limiting Pattern](https://redis.io/commands/incr/#pattern-rate-limiter)
- [Stripe: Scaling API Rate Limiting](https://stripe.com/blog/rate-limiters)
- [CloudFlare: How We Built Rate Limiting](https://blog.cloudflare.com/counting-things-a-lot-of-different-things/)
- [Kong: How to Design a Scalable Rate Limiting Algorithm](https://konghq.com/blog/how-to-design-a-scalable-rate-limiting-algorithm)
- [Figma: Implementing Rate Limiting](https://www.figma.com/blog/an-alternative-approach-to-rate-limiting/)

---
