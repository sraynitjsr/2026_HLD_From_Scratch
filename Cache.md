# Caching: A Complete Technical Guide

## Table of Contents
1. [Fundamentals](#fundamentals)
2. [Cache Hierarchy](#cache-hierarchy)
3. [Caching Strategies](#caching-strategies)
4. [Cache Eviction Policies](#cache-eviction-policies)
5. [Cache Invalidation](#cache-invalidation)
6. [Distributed Caching](#distributed-caching)
7. [Cache Coherence](#cache-coherence)
8. [Implementation Patterns](#implementation-patterns)
9. [Technologies & Tools](#technologies--tools)
10. [Performance Characteristics](#performance-characteristics)
11. [Advanced Concepts](#advanced-concepts)
12. [Anti-Patterns & Pitfalls](#anti-patterns--pitfalls)

---

## Fundamentals

### What is Caching?

Caching is a technique to store copies of data in a temporary storage location (cache) that allows faster access than fetching from the primary source. The cache sits between the client and the data source, serving requests when data is available (cache hit) and forwarding to the source when not (cache miss).

**Cache Hit**: Data found in cache (fast)  
**Cache Miss**: Data not in cache, fetch from origin (slow)  
**Hit Ratio**: `(Cache Hits / Total Requests) × 100%`

### Why Cache?

1. **Reduced Latency**: Access data orders of magnitude faster (RAM vs Disk: ~100x, RAM vs Network: ~1000x)
2. **Lower Load**: Reduce requests to origin servers/databases
3. **Cost Efficiency**: Cheaper to serve from cache than compute/fetch repeatedly
4. **Improved Throughput**: Handle more requests with same infrastructure
5. **Availability**: Serve stale data when origin is unavailable

### When to Use Caching

✅ **Good Candidates:**
- Frequently accessed data with infrequent updates
- Expensive computations with predictable inputs
- External API responses (with appropriate TTL)
- Static assets (images, CSS, JS)
- Session data
- Database query results
- Authentication tokens

❌ **Poor Candidates:**
- Highly volatile data requiring real-time accuracy
- User-specific data with low reuse
- Data with strict consistency requirements
- Large datasets with low access patterns

### Locality of Reference

Caching effectiveness relies on:

- **Temporal Locality**: Recently accessed data likely accessed again soon
- **Spatial Locality**: Data near recently accessed data likely accessed soon

---

## Cache Hierarchy

### 1. Client-Side Caching

**Browser Cache**
- Location: User's browser
- Controlled by: HTTP headers (Cache-Control, ETag, Expires)
- Use case: Static assets, public resources
- TTL: Hours to days

**Application Cache**
- Location: Client application memory
- Controlled by: Application logic
- Use case: In-memory data structures, UI state
- TTL: Session lifetime

### 2. CDN (Content Delivery Network) Cache

- Location: Edge servers globally distributed
- Purpose: Serve static content from geographically close locations
- Providers: Cloudflare, Akamai, AWS CloudFront, Fastly
- TTL: Minutes to days
- Use case: Images, videos, static files, API responses (with care)

### 3. Web Server Cache

**Reverse Proxy Cache**
- Tools: Varnish, Nginx, Apache mod_cache
- Location: In front of application servers
- Caches: Full HTTP responses
- TTL: Seconds to hours

### 4. Application-Level Cache

**In-Memory Cache**
- Location: Application server RAM
- Tools: Local dictionaries, Caffeine (Java), LRU caches
- Scope: Single process/instance
- TTL: Minutes to hours
- Pro: Fastest access (nanoseconds)
- Con: Not shared across instances

**Distributed Cache**
- Location: Separate cache servers/cluster
- Tools: Redis, Memcached, Hazelcast
- Scope: Shared across all application instances
- TTL: Minutes to days
- Pro: Shared state, scalable
- Con: Network latency (microseconds-milliseconds)

### 5. Database Caching

**Query Result Cache**
- Location: Database server memory
- Caches: Query results, execution plans
- Managed by: Database engine (MySQL query cache, PostgreSQL shared buffers)

**Object/Row Cache**
- Location: Between application and database
- Caches: Deserialized objects, table rows
- Tools: Hibernate second-level cache, ORM caching

### 6. CPU Cache

- L1 Cache: 32-64 KB, ~1 ns latency
- L2 Cache: 256-512 KB, ~3-10 ns latency
- L3 Cache: 2-32 MB, ~10-20 ns latency
- Managed by: Hardware, transparent to software

---

## Caching Strategies

### 1. Cache-Aside (Lazy Loading)

**Flow:**
```
1. Application checks cache
2. If miss: fetch from DB, write to cache, return
3. If hit: return from cache
```

**Characteristics:**
- Cache populated on-demand
- Application manages cache explicitly
- Stale data possible if DB updated externally

**Code Pattern:**
```python
def get_user(user_id):
    user = cache.get(f"user:{user_id}")
    if user is None:  # Cache miss
        user = db.query(f"SELECT * FROM users WHERE id={user_id}")
        cache.set(f"user:{user_id}", user, ttl=3600)
    return user
```

**Use case:** Read-heavy workloads

### 2. Read-Through

**Flow:**
```
1. Application requests from cache
2. Cache library checks data
3. If miss: cache fetches from DB automatically, stores, returns
```

**Characteristics:**
- Cache abstraction handles DB fetching
- Transparent to application
- Similar to cache-aside but library-managed

**Use case:** Simplify application code

### 3. Write-Through

**Flow:**
```
1. Application writes to cache
2. Cache synchronously writes to DB
3. Confirm to application after both succeed
```

**Characteristics:**
- Data always consistent between cache and DB
- Higher write latency (synchronous)
- No data loss on cache failure

**Use case:** Data consistency critical, write latency acceptable

### 4. Write-Behind (Write-Back)

**Flow:**
```
1. Application writes to cache
2. Cache confirms immediately
3. Cache asynchronously writes to DB later (batched)
```

**Characteristics:**
- Low write latency
- Risk of data loss if cache fails before DB write
- Improves write throughput via batching

**Use case:** Write-heavy workloads, eventual consistency acceptable

### 5. Write-Around

**Flow:**
```
1. Application writes directly to DB
2. Cache not updated
3. Next read causes cache miss, populates cache
```

**Characteristics:**
- Avoids cache pollution from one-time writes
- First read after write slower

**Use case:** Write-once, read-rarely data

### 6. Refresh-Ahead

**Flow:**
```
1. Cache predicts data will be requested
2. Asynchronously refreshes before expiration
3. Serve from cache while refreshing
```

**Characteristics:**
- Reduces cache misses for predictable access patterns
- Requires prediction logic
- Increased cache load

**Use case:** Predictable traffic patterns, critical latency requirements

---

## Cache Eviction Policies

When cache is full, which entry to remove?

### 1. LRU (Least Recently Used)

- **Logic:** Remove entry accessed longest ago
- **Implementation:** Doubly linked list + hash map (O(1) operations)
- **Use case:** General purpose, temporal locality strong
- **Pros:** Simple, effective for most workloads
- **Cons:** Sequential scans pollute cache

### 2. LFU (Least Frequently Used)

- **Logic:** Remove entry accessed least often
- **Implementation:** Min-heap or counter-based
- **Use case:** Access frequency matters more than recency
- **Pros:** Keeps popular items regardless of recency
- **Cons:** New items evicted quickly, stale popular items persist

### 3. FIFO (First In First Out)

- **Logic:** Remove oldest entry
- **Implementation:** Queue
- **Use case:** Simple scenarios, access patterns irrelevant
- **Pros:** Simple, predictable
- **Cons:** No consideration of usage patterns

### 4. Random Replacement

- **Logic:** Remove random entry
- **Implementation:** Random selection
- **Pros:** Simple, no overhead, surprisingly effective
- **Cons:** May evict hot data

### 5. TTL (Time To Live)

- **Logic:** Remove entries after fixed time
- **Implementation:** Expiration timestamp per entry
- **Use case:** Time-sensitive data
- **Pros:** Guarantees data freshness
- **Cons:** May evict still-useful data

### 6. ARC (Adaptive Replacement Cache)

- **Logic:** Balances recency and frequency dynamically
- **Implementation:** Two LRU lists (recent, frequent)
- **Use case:** Mixed access patterns
- **Pros:** Adapts to workload, better than LRU
- **Cons:** Complex implementation

### 7. LRU-K

- **Logic:** Track last K accesses, evict based on K-th most recent
- **Use case:** Database buffer pools
- **Pros:** Reduces scan pollution
- **Cons:** More memory overhead

### 8. 2Q (Two Queue)

- **Logic:** In-queue (FIFO) → main-queue (LRU)
- **Use case:** Filter one-time access from frequent access
- **Pros:** Reduces cache pollution
- **Cons:** Tuning required

---

## Cache Invalidation

> "There are only two hard things in Computer Science: cache invalidation and naming things." — Phil Karlton

### Invalidation Strategies

#### 1. TTL-Based (Time-based)

**Method:** Data expires after fixed duration

```
cache.set("key", value, ttl=300)  # 5 minutes
```

**Pros:**
- Simple, automatic cleanup
- Predictable behavior

**Cons:**
- May serve stale data before expiration
- May evict fresh data after expiration

**Use case:** Data with known freshness window

#### 2. Event-Based Invalidation

**Method:** Invalidate on data mutation events

```python
def update_user(user_id, data):
    db.update(user_id, data)
    cache.delete(f"user:{user_id}")
```

**Pros:**
- Cache always fresh after update
- No stale data

**Cons:**
- Requires coordination
- Miss on next read

**Use case:** Strong consistency requirements

#### 3. Write-Through Invalidation

**Method:** Update cache on every write

```python
def update_user(user_id, data):
    db.update(user_id, data)
    cache.set(f"user:{user_id}", data)
```

**Pros:**
- Cache always fresh
- No cache miss after update

**Cons:**
- More complex
- Cache may not be needed soon

#### 4. Tag-Based Invalidation

**Method:** Group related cache entries, invalidate by tag

```python
cache.set("user:123", data, tags=["users", "active_users"])
cache.invalidate_tag("active_users")  # Clears all tagged entries
```

**Use case:** Related data dependencies

#### 5. Version-Based Invalidation

**Method:** Include version in cache key

```
cache_key = f"user:{user_id}:v{version}"
```

**Pros:**
- Old versions coexist with new
- No explicit invalidation needed

**Cons:**
- Cache bloat with multiple versions

### Invalidation Patterns

#### Cache Stampede (Thundering Herd)

**Problem:** Cache expires → many requests hit DB simultaneously → overload

**Solutions:**

1. **Probabilistic Early Expiration:**
```python
early_expiry_time = expiry_time - random(0, beta * log(current_time))
```

2. **Locking/Semaphore:**
```python
if not cache.exists(key):
    with distributed_lock(f"lock:{key}"):
        if not cache.exists(key):  # Double check
            data = db.fetch()
            cache.set(key, data)
```

3. **Background Refresh:**
```python
if time_until_expiry < threshold:
    async_refresh(key)  # Refresh in background
```

---

## Distributed Caching

### Architecture Patterns

#### 1. Centralized Cache Cluster

**Design:** Multiple cache servers, clients connect to cluster

**Topology:**
- Master-Slave (Redis Sentinel)
- Cluster mode (Redis Cluster, Memcached)

**Consistency:** Replication lag possible

#### 2. Consistent Hashing

**Purpose:** Distribute keys across cache nodes

**Algorithm:**
```
1. Hash cache nodes onto ring (0 to 2^32-1)
2. Hash cache key
3. Assign to first node clockwise from key hash
```

**Benefits:**
- Minimal key redistribution when nodes added/removed
- Only K/N keys move (K=total keys, N=nodes)

**Virtual Nodes:** Each physical node mapped to multiple ring positions for balanced distribution

#### 3. Sharding

**Horizontal Partitioning:** Split data across cache nodes

**Methods:**
- Range-based: `shard = key_id % num_shards`
- Hash-based: `shard = hash(key) % num_shards`
- Consistent hashing

**Trade-offs:**
- Range: Simple but unbalanced
- Modulo: Balanced but resharding expensive
- Consistent: Minimal resharding

### Replication

**Master-Slave:**
- Writes to master, replicated to slaves
- Reads from slaves (scale reads)
- Asynchronous replication (eventual consistency)

**Multi-Master:**
- Writes to any node
- Conflict resolution required
- Higher complexity

### Cache Coherence in Distributed Systems

**Problem:** Multiple cache layers can become inconsistent

**Solutions:**

1. **Invalidation Propagation:**
   - Publish invalidation events
   - All cache layers subscribe and invalidate

2. **Versioning:**
   - Include version/timestamp in cached data
   - Validate before serving

3. **Lease-Based:**
   - Cache holds lease with expiration
   - Source invalidates leases on update

---

## Implementation Patterns

### 1. Cache Key Design

**Best Practices:**

```
Format: {namespace}:{entity}:{id}:{version}
Example: app:user:12345:v2
```

- Include namespace to avoid collisions
- Hierarchical structure for pattern-based invalidation
- Consider key length (Memcached 250 byte limit)

**Anti-pattern:**
```
❌ getUserData_12345_timestamp_random
✅ user:12345:profile
```

### 2. Cache Warming

**Cold Start Problem:** Empty cache after deployment/restart

**Solutions:**

1. **Pre-warming:** Load critical data before traffic
```python
def warm_cache():
    popular_user_ids = get_popular_users()
    for user_id in popular_user_ids:
        user = db.get_user(user_id)
        cache.set(f"user:{user_id}", user)
```

2. **Gradual Warming:** Route subset of traffic initially

### 3. Circuit Breaker Pattern

**Purpose:** Prevent cascade failures if cache unavailable

```python
class CacheCircuitBreaker:
    def get(self, key):
        if self.is_open():
            return None  # Fail fast
        try:
            return cache.get(key)
        except Exception:
            self.record_failure()
            return None
```

### 4. Multi-Level Caching

**Pattern:** L1 (local) + L2 (distributed)

```python
def get_data(key):
    # L1: In-memory
    data = local_cache.get(key)
    if data:
        return data
    
    # L2: Distributed
    data = redis.get(key)
    if data:
        local_cache.set(key, data)
        return data
    
    # Source
    data = db.fetch(key)
    redis.set(key, data)
    local_cache.set(key, data)
    return data
```

**Benefits:** Reduced network calls, faster access

### 5. Negative Caching

**Purpose:** Cache "not found" results to prevent repeated lookups

```python
def get_user(user_id):
    user = cache.get(f"user:{user_id}")
    if user == "NULL":  # Cached negative
        return None
    if user:
        return user
    
    user = db.get_user(user_id)
    if user is None:
        cache.set(f"user:{user_id}", "NULL", ttl=60)  # Cache absence
    else:
        cache.set(f"user:{user_id}", user, ttl=3600)
    return user
```

**Use case:** Prevent DB hammering for non-existent entities

---

## Technologies & Tools

### Redis

**Type:** In-memory data structure store

**Features:**
- Data structures: Strings, Lists, Sets, Sorted Sets, Hashes, Bitmaps, HyperLogLog, Streams
- Persistence: RDB snapshots, AOF logs
- Replication: Master-slave, Sentinel for HA
- Clustering: Automatic sharding
- Pub/Sub messaging
- Lua scripting
- Transactions (MULTI/EXEC)

**Performance:**
- Single-threaded (v6.0+: threaded I/O)
- ~100K ops/sec single instance
- Sub-millisecond latency

**Use case:** Complex data structures, persistence required, Pub/Sub

**Commands:**
```redis
SET key value EX 3600          # String with TTL
HSET user:123 name "John"      # Hash
LPUSH queue:tasks task1        # List
SADD set:users 123             # Set
ZADD leaderboard 100 user1     # Sorted set
```

### Memcached

**Type:** Distributed memory object caching

**Features:**
- Simple key-value store
- LRU eviction
- Multi-threaded
- No persistence
- Consistent hashing (client-side)

**Performance:**
- ~200K ops/sec single instance
- Sub-millisecond latency
- Lower memory overhead than Redis

**Use case:** Simple caching, maximum throughput, no persistence needed

**Commands:**
```
set key 0 3600 5         # key, flags, TTL, size
value
get key
delete key
```

### Hazelcast

**Type:** In-memory data grid (IMDG)

**Features:**
- Distributed data structures (Map, Queue, Topic)
- Embedded or client-server mode
- Automatic data partitioning
- Near cache (local L1 + distributed L2)
- WAN replication

**Use case:** Java applications, distributed computing, data locality

### Apache Ignite

**Type:** Distributed database and caching

**Features:**
- SQL support (ANSI-99)
- ACID transactions
- Distributed computing
- Persistence optional
- Multi-tier storage

**Use case:** Hybrid transactional/analytical workloads

### Varnish

**Type:** HTTP reverse proxy cache

**Features:**
- VCL (Varnish Configuration Language) for custom logic
- Edge Side Includes (ESI)
- Grace mode (serve stale on backend failure)

**Performance:**
- Handles 100K+ requests/sec
- RAM-based caching

**Use case:** HTTP caching, API gateway, CDN origin shield

### Caffeine (Java)

**Type:** High-performance in-memory cache library

**Features:**
- Near-optimal hit rate (W-TinyLFU eviction)
- Automatic loading
- Time-based expiration
- Size-based eviction
- Weak/soft reference eviction

**Use case:** Local JVM caching

---

## Performance Characteristics

### Latency Comparison

| Storage Type | Latency | Bandwidth |
|--------------|---------|-----------|
| L1 Cache | ~1 ns | - |
| L2 Cache | ~3-10 ns | - |
| L3 Cache | ~10-20 ns | - |
| Main Memory (RAM) | ~100 ns | 10-100 GB/s |
| Redis (local network) | 0.1-1 ms | 1-10 GB/s |
| SSD | 0.1-1 ms | 0.5-3 GB/s |
| HDD | 5-10 ms | 100-200 MB/s |
| Network (cross-AZ) | 1-5 ms | 1-10 GB/s |
| Network (cross-region) | 50-200 ms | Variable |

### Hit Ratio Impact

```
Response Time = Hit_Ratio × Cache_Latency + (1 - Hit_Ratio) × DB_Latency

Example:
- Cache: 1ms
- DB: 100ms
- Hit ratio: 90%

Response Time = 0.9 × 1 + 0.1 × 100 = 10.9ms

If hit ratio drops to 50%:
Response Time = 0.5 × 1 + 0.5 × 100 = 50.5ms
```

### Memory Estimation

**Cache Size Calculation:**
```
Cache Size = Num_Entries × (Key_Size + Value_Size + Overhead)

Example:
- 1M users
- Key: 20 bytes ("user:123456")
- Value: 1KB (user object)
- Overhead: 50 bytes (metadata, pointers)

Total = 1,000,000 × (20 + 1024 + 50) = ~1.04 GB
```

**Overhead:**
- Redis: ~25% overhead (pointers, metadata, alignment)
- Memcached: ~20% overhead

### Throughput Scaling

**Single Instance Limits:**
- Redis: ~100K ops/sec (CPU bound)
- Memcached: ~200K ops/sec (multi-threaded advantage)

**Scaling:**
- Vertical: Larger instance (limited)
- Horizontal: Sharding/clustering (linear scaling up to network limits)

---

## Advanced Concepts

### 1. Bloom Filters

**Purpose:** Probabilistic data structure to test set membership

**Use case:** Avoid cache lookups for definitely-absent keys

**Properties:**
- False positives possible (says "maybe in cache" when not)
- No false negatives (if says "not in cache", definitely not)
- Space efficient: ~10 bits per element for 1% false positive rate

**Implementation:**
```python
bloom = BloomFilter(size=1000000, fp_rate=0.01)

def get_data(key):
    if not bloom.contains(key):  # Definitely not cached
        return db.fetch(key)
    
    data = cache.get(key)  # May still miss
    if data is None:
        data = db.fetch(key)
        cache.set(key, data)
        bloom.add(key)
    return data
```

### 2. Cache Compression

**Trade-off:** CPU time vs memory/network

**When to compress:**
- Large values (>1KB)
- Sufficient CPU headroom
- Network bandwidth limited

**Algorithms:**
- Snappy: Fast, moderate compression (~2x)
- LZ4: Very fast, moderate compression (~2-3x)
- Zstandard: Configurable, good compression (~3-5x)
- Gzip: Slower, better compression (~5-10x)

**Implementation:**
```python
import zstd

def cache_set(key, value):
    compressed = zstd.compress(pickle.dumps(value))
    cache.set(key, compressed)

def cache_get(key):
    compressed = cache.get(key)
    return pickle.loads(zstd.decompress(compressed))
```

### 3. Tiered Caching

**Hot/Warm/Cold Tiers:**
- Hot: In-memory (Redis), microseconds
- Warm: SSD-backed (RocksDB), milliseconds
- Cold: Disk/S3, seconds

**Promotion/Demotion:**
- Access frequency threshold
- LRU across tiers

### 4. Probabilistic Data Structures

**Count-Min Sketch:** Approximate frequency counting
**HyperLogLog:** Cardinality estimation
**Top-K:** Track most frequent items

**Use case:** Analytics on cached data without full storage

### 5. Adaptive TTL

**Dynamic TTL based on:**
- Access frequency: Hot data → longer TTL
- Update frequency: Volatile data → shorter TTL
- Resource utilization: High load → longer TTL

```python
def adaptive_ttl(key):
    access_count = get_access_count(key)
    base_ttl = 300
    
    if access_count > 1000:
        return base_ttl * 4  # Very hot
    elif access_count > 100:
        return base_ttl * 2  # Hot
    else:
        return base_ttl      # Normal
```

### 6. Cache Mesh/Sidecar Pattern

**Architecture:** Cache instance colocated with each application instance

**Benefits:**
- No network latency
- Isolated failures
- Simple deployment

**Implementation:**
- Kubernetes sidecar container
- Service mesh integration

### 7. Read-Repair

**Purpose:** Fix inconsistencies during reads

**Flow:**
```
1. Read from cache (stale data)
2. Asynchronously validate against source
3. If mismatch, update cache
4. Return stale data (eventual consistency)
```

### 8. Cache Revalidation (ETags)

**HTTP Pattern:**
```
1. Cache stores ETag with data
2. On expiration, send conditional request: If-None-Match: <etag>
3. Server returns 304 Not Modified (data unchanged) or 200 with new data
4. Save bandwidth if unchanged
```

---

## Anti-Patterns & Pitfalls

### 1. Over-Caching

**Problem:** Cache everything indiscriminately

**Consequences:**
- Memory waste
- Increased complexity
- Stale data issues

**Solution:** Cache based on data: access frequency, update frequency, size

### 2. Under-Specified TTL

**Problem:** No TTL or extremely long TTL

**Consequences:**
- Stale data served indefinitely
- Memory bloat

**Solution:** Always set appropriate TTL based on data volatility

### 3. Cache Without Monitoring

**Problem:** No visibility into hit ratio, latency, memory usage

**Consequences:**
- Can't optimize
- Miss degradation

**Metrics to track:**
- Hit ratio
- Miss ratio
- Eviction rate
- Memory usage
- Latency (p50, p95, p99)
- Error rate

### 4. Ignoring Serialization Cost

**Problem:** Large objects with expensive serialization

**Consequences:**
- CPU overhead negates cache benefit

**Solution:**
- Cache serialized form
- Use efficient formats (Protocol Buffers, MessagePack vs JSON)

### 5. Large Cache Keys

**Problem:** Verbose, redundant keys

```
❌ "application_name:production:user_service:user:profile:id:12345:version:2"
✅ "app:user:12345:v2"
```

**Consequences:**
- Memory waste
- Network overhead

### 6. Cache Penetration

**Problem:** Queries for non-existent data bypass cache, hit DB repeatedly

**Solution:** Negative caching, Bloom filters

### 7. Cache Avalanche

**Problem:** Many cache entries expire simultaneously → DB overload

**Solution:** Stagger TTLs with randomization
```python
ttl = base_ttl + random.randint(0, base_ttl * 0.1)
```

### 8. Not Handling Cache Failures

**Problem:** Cache down → application crashes

**Solution:** Circuit breaker, graceful degradation
```python
try:
    return cache.get(key)
except CacheException:
    log.error("Cache unavailable")
    return db.fetch(key)  # Fallback
```

### 9. Caching Non-Serializable Objects

**Problem:** Language-specific objects, file handles, connections

**Consequences:**
- Serialization errors
- Stale references

**Solution:** Cache primitives, DTOs, immutable data

### 10. Distributed Cache Without Consistency Model

**Problem:** No strategy for cache coherence across instances

**Consequences:**
- Inconsistent views of data

**Solution:** Choose consistency model explicitly (eventual, strong, causal)

---

## Decision Matrix

| Requirement | Recommendation |
|-------------|----------------|
| Read-heavy, write-light | Cache-aside with high TTL |
| Write-heavy | Write-behind or skip caching writes |
| Strong consistency | Write-through or small TTL |
| Eventual consistency ok | Cache-aside or write-behind |
| Simple key-value | Memcached |
| Complex data structures | Redis |
| Java application | Caffeine (local) or Hazelcast (distributed) |
| HTTP/API responses | Varnish or CDN |
| Large dataset, low hit ratio | Skip caching or use tiered approach |
| High availability critical | Replicated Redis cluster |
| Sub-millisecond latency | Local in-memory cache |
| Shared state across instances | Distributed cache (Redis/Memcached) |

---

## Metrics & Formulas

### Hit Ratio
```
Hit Ratio = Cache Hits / (Cache Hits + Cache Misses)
Target: >90% for effective caching
```

### Miss Penalty
```
Miss Penalty = Cost(Cache Miss) - Cost(Cache Hit)
```

### Effective Access Time
```
EAT = Hit_Rate × Cache_Time + Miss_Rate × (Cache_Time + Fetch_Time)
```

### Memory Efficiency
```
Memory Efficiency = (Num_Hits × Value_Size) / Total_Cache_Size
```

### Eviction Rate
```
Eviction Rate = Evictions / Time_Period
High rate indicates undersized cache
```

### Working Set Size
```
Working Set = Unique keys accessed in time window
Cache Size should ≈ Working Set Size for optimal hit ratio
```

---

## Summary

**When to Cache:**
- High read-to-write ratio (>10:1 ideal)
- Expensive computation/fetch
- Acceptable staleness window
- Repeatable queries

**When NOT to Cache:**
- Real-time data requirements
- High write rate with immediate consistency needs
- Unpredictable access patterns (random, no locality)
- Large data with storage constraints

**Key Principles:**
1. Measure before optimizing (hit ratio, latency)
2. Choose appropriate cache level (client/server/database)
3. Set appropriate TTL based on volatility
4. Plan invalidation strategy upfront
5. Handle cache failures gracefully
6. Monitor continuously (hit ratio, memory, latency)

**Common Pattern:**
```
L1 (Local): Caffeine/in-memory for hot data (ms-level TTL)
L2 (Distributed): Redis for shared data (minutes-hours TTL)
L3 (CDN): CloudFront for static assets (hours-days TTL)
```

Caching is a trade-off between consistency and performance. Choose the strategy that aligns with your consistency requirements and access patterns.
