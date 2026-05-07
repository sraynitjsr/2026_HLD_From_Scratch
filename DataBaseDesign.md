# Database Design: A Complete Technical Guide

## Table of Contents
1. [Fundamentals](#fundamentals)
2. [SQL vs NoSQL](#sql-vs-nosql)
3. [Database Normalization](#database-normalization)
4. [Indexing](#indexing)
5. [Partitioning & Sharding](#partitioning--sharding)
6. [Replication](#replication)
7. [CAP Theorem](#cap-theorem)
8. [ACID vs BASE](#acid-vs-base)
9. [Query Optimization](#query-optimization)
10. [Database Types](#database-types)
11. [Schema Design Patterns](#schema-design-patterns)
12. [Scaling Strategies](#scaling-strategies)
13. [Backup & Recovery](#backup--recovery)
14. [Anti-Patterns & Pitfalls](#anti-patterns--pitfalls)
15. [Real-World Examples](#real-world-examples)

---

## Fundamentals

### What is a Database?

A database is an organized collection of structured data stored electronically, designed for efficient data retrieval, storage, and manipulation. Databases provide:

- **Persistence**: Data survives beyond application lifecycle
- **Concurrency**: Multiple users access data simultaneously
- **Consistency**: Data integrity maintained through constraints
- **Query Language**: Structured way to retrieve/manipulate data
- **Transactions**: Atomic operations ensuring data consistency

### Key Concepts

**Schema**: Structure defining tables, columns, relationships, constraints

**Transaction**: Sequence of operations treated as single unit (all succeed or all fail)

**Index**: Data structure improving query performance

**Constraint**: Rules enforcing data integrity (PRIMARY KEY, FOREIGN KEY, UNIQUE, NOT NULL)

**Normalization**: Process of organizing data to reduce redundancy

**Denormalization**: Intentionally adding redundancy to improve read performance

---

## SQL vs NoSQL

### SQL (Relational Databases)

**Characteristics:**
- Structured schema (predefined tables, columns, types)
- ACID compliance
- Relational model (tables with relationships)
- SQL query language
- Vertical scaling traditional (horizontal possible with sharding)

**Examples:** PostgreSQL, MySQL, Oracle, SQL Server, SQLite

**Strengths:**
- ✅ Strong consistency
- ✅ Complex queries with JOINs
- ✅ Data integrity (foreign keys, constraints)
- ✅ Transaction support
- ✅ Mature ecosystem and tooling
- ✅ ACID guarantees

**Weaknesses:**
- ❌ Rigid schema (schema changes expensive)
- ❌ Harder to scale horizontally
- ❌ Performance degradation with massive scale
- ❌ Not optimal for hierarchical/nested data

**Use Cases:**
- Financial systems (banking, payments)
- E-commerce (orders, inventory)
- CRM/ERP systems
- Systems requiring complex queries
- Data requiring strong consistency

### NoSQL (Non-Relational Databases)

**Characteristics:**
- Flexible/dynamic schema
- Horizontal scaling built-in
- Eventual consistency (typically)
- Specialized query languages
- Optimized for specific data models

#### 1. Document Databases

**Model:** Store data as documents (JSON, BSON, XML)

**Examples:** MongoDB, Couchbase, CouchDB

**Structure:**
```json
{
  "_id": "user123",
  "name": "John Doe",
  "email": "john@example.com",
  "addresses": [
    {
      "type": "home",
      "street": "123 Main St",
      "city": "Boston"
    }
  ],
  "tags": ["premium", "verified"]
}
```

**Strengths:**
- ✅ Flexible schema
- ✅ Nested data naturally represented
- ✅ Easy to scale horizontally
- ✅ Developer-friendly (JSON)

**Weaknesses:**
- ❌ No JOINs (require application-level joins)
- ❌ Data duplication
- ❌ Eventually consistent (by default)

**Use Cases:**
- Content management systems
- User profiles
- Product catalogs
- Event logging
- Real-time analytics

#### 2. Key-Value Stores

**Model:** Simple key → value mapping

**Examples:** Redis, DynamoDB, Riak, Memcached

**Structure:**
```
user:123 → {"name": "John", "email": "john@example.com"}
session:abc → "user_id=123&expires=1234567890"
counter:views → 42
```

**Strengths:**
- ✅ Extremely fast (O(1) lookups)
- ✅ Simple model
- ✅ Highly scalable
- ✅ Low latency

**Weaknesses:**
- ❌ No complex queries
- ❌ Limited data modeling
- ❌ No relationships

**Use Cases:**
- Session storage
- Caching
- Real-time recommendations
- Leaderboards
- Rate limiting

#### 3. Column-Family Stores

**Model:** Data stored in column families, rows can have different columns

**Examples:** Cassandra, HBase, ScyllaDB

**Structure:**
```
Row Key: user123
Column Family: profile
  - name: "John Doe"
  - email: "john@example.com"
Column Family: activity
  - last_login: 1234567890
  - login_count: 42
```

**Strengths:**
- ✅ Excellent for write-heavy workloads
- ✅ Handles massive scale (petabytes)
- ✅ Time-series data optimization
- ✅ Tunable consistency

**Weaknesses:**
- ❌ Complex to model
- ❌ Limited query flexibility
- ❌ Eventual consistency

**Use Cases:**
- Time-series data (IoT, metrics)
- Event logging
- Analytics
- Recommendation engines

#### 4. Graph Databases

**Model:** Nodes (entities) and edges (relationships)

**Examples:** Neo4j, Amazon Neptune, ArangoDB

**Structure:**
```
(User:John)-[:FOLLOWS]->(User:Jane)
(User:John)-[:LIKES]->(Post:123)
(Post:123)-[:TAGGED]->(Tag:Database)
```

**Strengths:**
- ✅ Natural representation of relationships
- ✅ Fast relationship traversal
- ✅ Complex relationship queries
- ✅ Pattern matching

**Weaknesses:**
- ❌ Not suitable for all data types
- ❌ Harder to scale (sharding relationships)
- ❌ Learning curve (Cypher, Gremlin)

**Use Cases:**
- Social networks
- Fraud detection
- Recommendation engines
- Knowledge graphs
- Network topology

### Decision Matrix

| Requirement | SQL | NoSQL |
|-------------|-----|-------|
| Complex queries with JOINs | ✅ Excellent | ❌ Limited |
| Flexible schema | ❌ Rigid | ✅ Dynamic |
| Horizontal scaling | 🟡 Possible (complex) | ✅ Built-in |
| Strong consistency | ✅ ACID | 🟡 Tunable |
| Transaction support | ✅ Full ACID | 🟡 Limited |
| Write-heavy workload | 🟡 Good | ✅ Excellent |
| Read-heavy workload | ✅ Excellent (with indexes) | ✅ Excellent |
| Data integrity | ✅ Constraints | 🟡 Application-level |
| Schema evolution | ❌ Migrations required | ✅ Easy |

---

## Database Normalization

### What is Normalization?

Process of organizing database to:
1. Reduce data redundancy
2. Improve data integrity
3. Eliminate anomalies (insertion, update, deletion)

### Normal Forms

#### 1NF (First Normal Form)

**Rules:**
- Each column contains atomic (indivisible) values
- Each column contains values of single type
- Each row is unique (has primary key)
- No repeating groups

**Before (not 1NF):**
```sql
| OrderID | CustomerName | Products               |
|---------|--------------|------------------------|
| 1       | John         | Laptop, Mouse, Keyboard|
```

**After (1NF):**
```sql
| OrderID | CustomerName | Product  |
|---------|--------------|----------|
| 1       | John         | Laptop   |
| 1       | John         | Mouse    |
| 1       | John         | Keyboard |
```

#### 2NF (Second Normal Form)

**Rules:**
- Must be in 1NF
- All non-key columns fully dependent on primary key (no partial dependencies)

**Before (1NF, not 2NF):**
```sql
| OrderID | ProductID | ProductName | Quantity | Price |
|---------|-----------|-------------|----------|-------|
| 1       | 101       | Laptop      | 1        | 1000  |
| 1       | 102       | Mouse       | 2        | 25    |
```
Problem: ProductName depends only on ProductID, not full key (OrderID, ProductID)

**After (2NF):**
```sql
Orders:
| OrderID | ProductID | Quantity |
|---------|-----------|----------|
| 1       | 101       | 1        |
| 1       | 102       | 2        |

Products:
| ProductID | ProductName | Price |
|-----------|-------------|-------|
| 101       | Laptop      | 1000  |
| 102       | Mouse       | 25    |
```

#### 3NF (Third Normal Form)

**Rules:**
- Must be in 2NF
- No transitive dependencies (non-key columns depend only on primary key, not other non-key columns)

**Before (2NF, not 3NF):**
```sql
| EmployeeID | DepartmentID | DepartmentName | DepartmentLocation |
|------------|--------------|----------------|-------------------|
| 1          | 10           | Engineering    | Building A        |
| 2          | 10           | Engineering    | Building A        |
```
Problem: DepartmentName and DepartmentLocation depend on DepartmentID, not EmployeeID

**After (3NF):**
```sql
Employees:
| EmployeeID | DepartmentID |
|------------|--------------|
| 1          | 10           |
| 2          | 10           |

Departments:
| DepartmentID | DepartmentName | DepartmentLocation |
|--------------|----------------|-------------------|
| 10           | Engineering    | Building A        |
```

#### BCNF (Boyce-Codd Normal Form)

**Rules:**
- Stricter version of 3NF
- Every determinant must be a candidate key

**Use case:** Rare edge cases with multiple overlapping candidate keys

#### 4NF & 5NF

**4NF:** No multi-valued dependencies  
**5NF:** No join dependencies

**Note:** 3NF is typically sufficient for most applications. Higher forms address rare scenarios.

### When to Denormalize

Normalization isn't always optimal. Denormalize when:

1. **Read performance critical**: Avoid expensive JOINs
2. **Read-heavy workloads**: Data rarely updated
3. **Reporting/Analytics**: Pre-computed aggregates
4. **Caching layer exists**: Handle inconsistencies

**Example:**
```sql
-- Normalized (requires JOIN)
SELECT o.order_id, o.total, c.name
FROM orders o
JOIN customers c ON o.customer_id = c.id

-- Denormalized (single table)
SELECT order_id, total, customer_name
FROM orders
```

**Trade-off:** Faster reads, but updates must maintain consistency across duplicated data.

---

## Indexing

### What is an Index?

Data structure (typically B-tree or Hash) that improves speed of data retrieval at the cost of:
- Additional storage space
- Slower writes (index must be updated)

**Analogy:** Like book index - quickly find pages without reading entire book.

### Index Types

#### 1. Primary Index

- Automatically created on PRIMARY KEY
- Unique, not null
- Table data physically sorted by primary key (clustered)

```sql
CREATE TABLE users (
    id INT PRIMARY KEY,  -- Primary index on id
    name VARCHAR(100)
);
```

#### 2. Secondary Index

- Created on non-primary columns
- Stores pointers to actual data

```sql
CREATE INDEX idx_email ON users(email);
```

#### 3. Unique Index

- Enforces uniqueness constraint
- Prevents duplicate values

```sql
CREATE UNIQUE INDEX idx_username ON users(username);
```

#### 4. Composite Index (Multi-Column)

- Index on multiple columns
- Column order matters for query optimization

```sql
CREATE INDEX idx_name_email ON users(last_name, first_name);
```

**Query optimization:**
```sql
-- Uses index
SELECT * FROM users WHERE last_name = 'Smith';
SELECT * FROM users WHERE last_name = 'Smith' AND first_name = 'John';

-- Does NOT use index (missing leading column)
SELECT * FROM users WHERE first_name = 'John';
```

#### 5. Full-Text Index

- Optimized for text search
- Supports natural language queries

```sql
CREATE FULLTEXT INDEX idx_content ON articles(title, body);
SELECT * FROM articles WHERE MATCH(title, body) AGAINST('database design');
```

#### 6. Covering Index

- Index contains all columns needed by query
- No table lookup required (index-only scan)

```sql
-- Query needs id, email, name
CREATE INDEX idx_covering ON users(email, name, id);

-- This query reads only from index
SELECT id, name FROM users WHERE email = 'john@example.com';
```

#### 7. Partial Index

- Index on subset of rows (PostgreSQL feature)

```sql
CREATE INDEX idx_active_users ON users(email) WHERE status = 'active';
```

### Index Data Structures

#### B-Tree (Balanced Tree)

**Default index type** in most databases.

**Structure:**
```
          [M]
        /     \
    [D, G]    [P, T]
   / | \      / | \
  A  E  H    N  Q  V
```

**Characteristics:**
- Self-balancing tree
- O(log n) search, insert, delete
- Range queries efficient
- Maintains sorted order

**Use case:** General-purpose indexing, range queries

#### Hash Index

**Structure:** Hash table with hash(key) → row_pointer

**Characteristics:**
- O(1) average case lookup
- No range query support
- Equality comparisons only

**Use case:** Exact match lookups (session IDs, cache keys)

```sql
-- PostgreSQL
CREATE INDEX idx_hash_email ON users USING HASH(email);
```

#### Bitmap Index

**Structure:** Bit array for each distinct value

**Example:**
```
Gender column:
Male:   [1, 0, 1, 0, 1, 0]
Female: [0, 1, 0, 1, 0, 1]
```

**Characteristics:**
- Excellent for low-cardinality columns (few distinct values)
- Efficient for boolean operations (AND, OR)
- High compression

**Use case:** Data warehouses, analytics

#### GiST & GIN (PostgreSQL)

**GiST (Generalized Search Tree):** Full-text search, spatial data  
**GIN (Generalized Inverted Index):** Array/JSON data, full-text

### Indexing Best Practices

#### ✅ DO Index

1. **Primary keys** (automatic)
2. **Foreign keys** (for JOINs)
3. **WHERE clause columns** (frequently filtered)
4. **ORDER BY columns** (frequently sorted)
5. **GROUP BY columns** (aggregations)
6. **Columns in JOINs**

#### ❌ DON'T Index

1. **Small tables** (full scan faster)
2. **High-cardinality columns with updates** (index maintenance cost)
3. **Columns with many NULLs**
4. **Every column** (diminishing returns, write penalty)

### Index Monitoring

**Check index usage (PostgreSQL):**
```sql
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;
```

**Unused indexes (idx_scan = 0):** Consider dropping to improve write performance.

**Missing indexes:** Use EXPLAIN ANALYZE to identify.

---

## Partitioning & Sharding

### Partitioning (Single Database)

Dividing large table into smaller, manageable pieces while appearing as single logical table.

#### 1. Horizontal Partitioning (Row-based)

Split rows across multiple partitions based on key.

**Range Partitioning:**
```sql
-- PostgreSQL
CREATE TABLE orders (
    order_id BIGINT,
    order_date DATE,
    customer_id INT,
    total DECIMAL
) PARTITION BY RANGE (order_date);

CREATE TABLE orders_2024 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE orders_2025 PARTITION OF orders
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
```

**List Partitioning:**
```sql
CREATE TABLE users_by_region PARTITION BY LIST (region);

CREATE TABLE users_us PARTITION OF users_by_region
    FOR VALUES IN ('US');

CREATE TABLE users_eu PARTITION OF users_by_region
    FOR VALUES IN ('EU', 'UK');
```

**Hash Partitioning:**
```sql
CREATE TABLE events PARTITION BY HASH (user_id);

CREATE TABLE events_0 PARTITION OF events
    FOR VALUES WITH (MODULUS 4, REMAINDER 0);

CREATE TABLE events_1 PARTITION OF events
    FOR VALUES WITH (MODULUS 4, REMAINDER 1);
-- ... events_2, events_3
```

**Benefits:**
- Improved query performance (partition pruning)
- Easier maintenance (drop old partitions)
- Parallel query execution

**Use Cases:**
- Time-series data (partition by date)
- Multi-tenant (partition by tenant_id)
- Geographically distributed users

#### 2. Vertical Partitioning (Column-based)

Split columns across tables.

**Example:**
```sql
-- Frequently accessed columns
CREATE TABLE users_core (
    user_id INT PRIMARY KEY,
    username VARCHAR(50),
    email VARCHAR(100)
);

-- Rarely accessed columns
CREATE TABLE users_extended (
    user_id INT PRIMARY KEY,
    bio TEXT,
    preferences JSON,
    FOREIGN KEY (user_id) REFERENCES users_core(user_id)
);
```

**Benefits:**
- Smaller table scans
- Better cache utilization
- Separate hot/cold data

### Sharding (Distributed Database)

Horizontal partitioning across multiple database servers (shared-nothing architecture).

#### Sharding Strategies

**1. Range-Based Sharding**

```
Shard 1: user_id 1 - 1,000,000
Shard 2: user_id 1,000,001 - 2,000,000
Shard 3: user_id 2,000,001 - 3,000,000
```

**Pros:**
- Simple logic
- Range queries efficient within shard

**Cons:**
- Unbalanced distribution (hotspots)
- Difficult to rebalance

**2. Hash-Based Sharding**

```python
shard = hash(user_id) % num_shards

# Example:
hash(12345) % 4 = 1  → Shard 1
hash(67890) % 4 = 3  → Shard 3
```

**Pros:**
- Uniform distribution
- Balanced load

**Cons:**
- Range queries require all shards
- Resharding expensive (data migration)

**3. Consistent Hashing**

**Algorithm:**
1. Hash shards onto ring (0 to 2^32-1)
2. Hash key
3. Assign to first shard clockwise

**Pros:**
- Minimal data movement on shard addition/removal
- Balanced with virtual nodes

**Cons:**
- More complex implementation

**4. Geographic Sharding**

```
Shard US-East: US users
Shard EU-West: EU users
Shard Asia-Pacific: APAC users
```

**Pros:**
- Low latency (data close to users)
- Regulatory compliance (data residency)

**Cons:**
- Cross-region queries expensive
- Unbalanced load

**5. Entity/Tenant-Based Sharding**

```
Shard 1: Company A, Company B
Shard 2: Company C, Company D
```

**Pros:**
- Data locality (tenant data together)
- Easier backup/migration per tenant

**Cons:**
- Large tenants cause hotspots

#### Sharding Challenges

**1. Cross-Shard Queries**

```sql
-- All shards must be queried
SELECT COUNT(*) FROM users;  -- Scatter-gather

-- Single shard query
SELECT * FROM users WHERE user_id = 12345;  -- Direct routing
```

**Solution:** Denormalize data or use secondary index service.

**2. Distributed Transactions**

**Problem:** ACID transactions across shards difficult.

**Solutions:**
- Avoid cross-shard transactions (design around shard key)
- Two-phase commit (slow, complex)
- Eventual consistency (Saga pattern)

**3. Resharding**

**Problem:** Changing number of shards requires data migration.

**Solutions:**
- Consistent hashing (minimize movement)
- Virtual shards (overshard, then map to physical shards)
- Gradual migration with dual writes

**4. Hotspots**

**Problem:** Uneven distribution (celebrity problem, popular content).

**Solutions:**
- Better shard key selection
- Horizontal scaling of hot shards
- Caching

#### Shard Key Selection

**Good Shard Key Characteristics:**
- High cardinality (many distinct values)
- Evenly distributed
- Aligns with query patterns (avoid cross-shard queries)
- Immutable (changing key requires migration)

**Examples:**
- ✅ `user_id` - High cardinality, immutable, most queries user-scoped
- ✅ `email` - Unique, immutable
- ❌ `country` - Low cardinality, hotspots
- ❌ `timestamp` - Monotonically increasing, unbalanced writes

---

## Replication

### What is Replication?

Copying and maintaining database data across multiple servers for:
- **High availability**: Failover if primary fails
- **Scalability**: Distribute read load
- **Disaster recovery**: Geographic redundancy

### Replication Topologies

#### 1. Master-Slave (Primary-Replica)

```
      Master (Read/Write)
         |
    +---------+---------+
    |         |         |
  Slave     Slave     Slave
  (Read)    (Read)    (Read)
```

**Characteristics:**
- Single master handles writes
- Slaves replicate from master
- Slaves handle reads
- Asynchronous or synchronous replication

**Pros:**
- Scale reads horizontally
- Simple architecture
- Read replicas don't impact write performance

**Cons:**
- Single point of failure (master)
- Replication lag (stale reads on slaves)
- Write scalability limited

**Use Case:** Read-heavy applications (blogs, news sites, dashboards)

#### 2. Master-Master (Multi-Master)

```
  Master 1 ←→ Master 2
    ↕            ↕
  Slave      Slave
```

**Characteristics:**
- Multiple masters accept writes
- Masters replicate to each other
- Conflict resolution required

**Pros:**
- Write scalability
- No single point of failure
- Geographic distribution

**Cons:**
- Conflict resolution complexity
- Eventual consistency
- Network partition issues

**Use Case:** Multi-region deployments, high write throughput

#### 3. Cascading Replication

```
     Master
       |
     Slave 1
       |
    +--+--+
    |     |
  Slave Slave
    2     3
```

**Characteristics:**
- Slaves replicate from other slaves
- Reduces load on master

**Use Case:** Large number of replicas

### Replication Methods

#### 1. Synchronous Replication

**Process:**
1. Master receives write
2. Master sends to replicas
3. Wait for replicas to acknowledge
4. Confirm to client

**Pros:**
- Strong consistency
- No data loss

**Cons:**
- High latency (wait for replicas)
- Availability impact (if replica down)

**Use Case:** Financial transactions, critical data

#### 2. Asynchronous Replication

**Process:**
1. Master receives write
2. Confirm to client immediately
3. Replicate to slaves in background

**Pros:**
- Low latency
- High availability

**Cons:**
- Eventual consistency
- Potential data loss (if master fails before replication)

**Use Case:** Most applications (default for MySQL, PostgreSQL)

#### 3. Semi-Synchronous Replication

**Process:**
1. Master receives write
2. Wait for at least 1 replica to acknowledge
3. Confirm to client

**Pros:**
- Balance of consistency and performance
- Reduced data loss risk

**Use Case:** Production systems requiring durability

### Replication Lag

**Definition:** Time difference between write on master and appearance on replica.

**Causes:**
- Network latency
- Replica overloaded (reads)
- Large transactions
- Slow queries on replica

**Solutions:**
1. **Monitor lag:**
```sql
-- PostgreSQL
SELECT now() - pg_last_xact_replay_timestamp() AS replication_lag;
```

2. **Read from master for critical data:**
```python
# Stale-tolerant read
user = read_from_replica(user_id)

# Fresh read (after write)
update_user(user_id)
user = read_from_master(user_id)  # Ensure consistency
```

3. **Application-level routing:**
   - Session consistency (stick to master after write)
   - Causal consistency (track version/timestamp)

### Failover

**Manual Failover:**
1. Detect master failure
2. Administrator promotes replica to master
3. Redirect application to new master

**Automatic Failover (High Availability):**

**Tools:**
- **MySQL:** MHA (Master High Availability), Orchestrator
- **PostgreSQL:** Patroni, repmgr
- **Redis:** Redis Sentinel

**Process:**
1. Monitor master health
2. Quorum detects failure
3. Elect new master (most up-to-date replica)
4. Redirect traffic via VIP or DNS
5. Inform other replicas of new master

**Challenges:**
- Split-brain (two masters)
- Data loss (if async replication)
- Client connection disruption

---

## CAP Theorem

### The Theorem

In a distributed system, you can only guarantee **2 out of 3**:

1. **Consistency (C)**: All nodes see the same data at the same time
2. **Availability (A)**: Every request receives a response (success or failure)
3. **Partition Tolerance (P)**: System continues despite network partitions

### Trade-offs

**Network partitions are inevitable**, so choice is between:

#### CP (Consistency + Partition Tolerance)

Sacrifice availability during partition.

**Behavior:** If nodes can't communicate, reject writes to maintain consistency.

**Examples:**
- MongoDB (with appropriate settings)
- HBase
- Redis (with synchronous replication)
- Zookeeper

**Use Case:** Financial systems, inventory management (data accuracy critical)

#### AP (Availability + Partition Tolerance)

Sacrifice consistency during partition.

**Behavior:** Accept writes on all partitions, resolve conflicts later (eventual consistency).

**Examples:**
- Cassandra
- DynamoDB
- Riak
- CouchDB

**Use Case:** Social media, shopping carts (temporary inconsistency acceptable)

#### CA (Consistency + Availability)

**Reality:** Not possible in distributed systems with network partitions.

**Exception:** Single-node databases or systems assuming no partitions (impractical at scale).

### PACELC Theorem (Extension of CAP)

```
IF Partition (P):
    Choose Availability (A) or Consistency (C)
ELSE (E):
    Choose Latency (L) or Consistency (C)
```

**Insight:** Even without partitions, there's a latency-consistency trade-off.

**Examples:**
- **PA/EL:** Cassandra, DynamoDB (prioritize availability and low latency)
- **PC/EC:** HBase, MongoDB (prioritize consistency)
- **PA/EC:** Cosmos DB (tunable)

---

## ACID vs BASE

### ACID (SQL Databases)

**Atomicity:**
- Transaction is all-or-nothing
- If part fails, entire transaction rolls back

**Consistency:**
- Database moves from one valid state to another
- Constraints enforced (foreign keys, unique, check)

**Isolation:**
- Concurrent transactions don't interfere
- Isolation levels: Read Uncommitted, Read Committed, Repeatable Read, Serializable

**Durability:**
- Committed transaction survives crashes
- Written to persistent storage (disk)

**Example:**
```sql
BEGIN TRANSACTION;
    UPDATE accounts SET balance = balance - 100 WHERE id = 1;
    UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;
-- Both updates succeed or both fail (atomicity)
```

### BASE (NoSQL Databases)

**Basically Available:**
- System guarantees availability (may be degraded)

**Soft state:**
- State may change over time (even without input)
- Replicas may be temporarily inconsistent

**Eventual consistency:**
- Given enough time, all replicas converge to same state

**Example:**
```
Time 0: Write to Node A (balance = 100)
Time 1: Read from Node B (balance = 0) ← Stale
Time 5: Replication completes
Time 6: Read from Node B (balance = 100) ← Consistent
```

### Choosing Between ACID and BASE

| Requirement | ACID (SQL) | BASE (NoSQL) |
|-------------|------------|--------------|
| Strong consistency | ✅ | ❌ |
| High availability | 🟡 | ✅ |
| Partition tolerance | 🟡 | ✅ |
| Complex transactions | ✅ | ❌ |
| Horizontal scaling | ❌ | ✅ |
| Low latency at scale | 🟡 | ✅ |
| Data integrity | ✅ | 🟡 |
| Simple operations | ✅ | ✅ |

---

## Query Optimization

### Understanding EXPLAIN

**Purpose:** Shows query execution plan.

```sql
EXPLAIN SELECT * FROM users WHERE email = 'john@example.com';
```

**Output (simplified):**
```
Seq Scan on users  (cost=0.00..1000.00 rows=1 width=100)
  Filter: (email = 'john@example.com')
```

**Key Metrics:**
- **Seq Scan:** Full table scan (slow for large tables)
- **Index Scan:** Uses index (fast)
- **cost:** Estimated execution cost
- **rows:** Estimated rows returned
- **width:** Average row size (bytes)

**With index:**
```
Index Scan using idx_email on users  (cost=0.28..8.30 rows=1 width=100)
  Index Cond: (email = 'john@example.com')
```

### Query Optimization Techniques

#### 1. Use Indexes

```sql
-- Slow (full table scan)
SELECT * FROM users WHERE email = 'john@example.com';

-- Fast (add index)
CREATE INDEX idx_email ON users(email);
```

#### 2. Avoid SELECT *

```sql
-- Bad (retrieves unnecessary columns)
SELECT * FROM users WHERE id = 123;

-- Good (only needed columns)
SELECT id, name, email FROM users WHERE id = 123;
```

#### 3. Use LIMIT

```sql
-- Bad (returns all matching rows)
SELECT * FROM orders WHERE status = 'pending';

-- Good (limit results)
SELECT * FROM orders WHERE status = 'pending' LIMIT 100;
```

#### 4. Optimize JOINs

```sql
-- Ensure JOIN columns are indexed
CREATE INDEX idx_customer_id ON orders(customer_id);
CREATE INDEX idx_id ON customers(id);

-- JOIN
SELECT o.order_id, c.name
FROM orders o
JOIN customers c ON o.customer_id = c.id;
```

**JOIN Types:**
- **INNER JOIN:** Fastest (only matching rows)
- **LEFT JOIN:** Slower (all left table rows)
- **CROSS JOIN:** Slowest (Cartesian product)

#### 5. Use EXISTS over IN for Subqueries

```sql
-- Slower (subquery materializes full list)
SELECT * FROM customers
WHERE id IN (SELECT customer_id FROM orders WHERE total > 1000);

-- Faster (stops at first match)
SELECT * FROM customers c
WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id AND o.total > 1000);
```

#### 6. Avoid Functions on Indexed Columns

```sql
-- Doesn't use index
SELECT * FROM users WHERE LOWER(email) = 'john@example.com';

-- Uses index (store email in lowercase or use functional index)
CREATE INDEX idx_email_lower ON users(LOWER(email));
SELECT * FROM users WHERE LOWER(email) = 'john@example.com';
```

#### 7. Use Covering Indexes

```sql
-- Index includes all queried columns (no table lookup)
CREATE INDEX idx_covering ON users(email, name, created_at);

SELECT name, created_at FROM users WHERE email = 'john@example.com';
-- Reads only from index
```

#### 8. Batch Operations

```sql
-- Slow (N queries)
for id in ids:
    DELETE FROM items WHERE id = id;

-- Fast (1 query)
DELETE FROM items WHERE id IN (1, 2, 3, 4, 5);
```

#### 9. Use Query Cache (if available)

```sql
-- MySQL query cache (deprecated in MySQL 8.0+)
SELECT SQL_CACHE * FROM products WHERE category = 'electronics';
```

**Modern alternative:** Application-level caching (Redis)

#### 10. Analyze and Vacuum (PostgreSQL)

```sql
-- Update statistics for query planner
ANALYZE users;

-- Reclaim storage, update statistics
VACUUM ANALYZE users;
```

### N+1 Query Problem

**Problem:** Fetching related data in loop.

```python
# Bad: N+1 queries (1 + N)
users = db.query("SELECT * FROM users")
for user in users:
    orders = db.query(f"SELECT * FROM orders WHERE user_id = {user.id}")
```

**Solution:** Use JOIN or eager loading.

```python
# Good: 1 query
results = db.query("""
    SELECT u.*, o.*
    FROM users u
    LEFT JOIN orders o ON u.id = o.user_id
""")
```

### Database Statistics

**Keep statistics up-to-date** for optimal query plans.

```sql
-- PostgreSQL
ANALYZE;

-- MySQL
ANALYZE TABLE users;
```

---

## Database Types

### 1. Relational Databases (RDBMS)

**Examples:** PostgreSQL, MySQL, Oracle, SQL Server

**Best For:**
- Structured data with relationships
- Complex queries with JOINs
- Transactions (ACID)
- Financial systems, e-commerce

**PostgreSQL:**
- Open-source, feature-rich
- JSON support (JSONB)
- Full-text search
- Extensibility (custom types, functions)

**MySQL:**
- Widely used, simple setup
- Fast for read-heavy workloads
- Strong community support

### 2. Document Databases

**Examples:** MongoDB, Couchbase

**Best For:**
- Flexible schema
- Nested/hierarchical data
- Content management
- Product catalogs

**MongoDB:**
- JSON-like documents (BSON)
- Horizontal scaling (sharding)
- Aggregation framework
- Rich query language

### 3. Key-Value Stores

**Examples:** Redis, DynamoDB, Riak

**Best For:**
- Caching
- Session storage
- Simple lookups
- Real-time applications

**Redis:**
- In-memory (extremely fast)
- Data structures (strings, lists, sets, hashes, sorted sets)
- Pub/Sub messaging
- Persistence optional

**DynamoDB:**
- Fully managed (AWS)
- Predictable performance at scale
- Auto-scaling
- Single-digit millisecond latency

### 4. Column-Family Stores

**Examples:** Cassandra, HBase, ScyllaDB

**Best For:**
- Write-heavy workloads
- Time-series data
- Analytics
- IoT applications

**Cassandra:**
- Linear scalability
- Multi-datacenter replication
- Tunable consistency
- No single point of failure

### 5. Graph Databases

**Examples:** Neo4j, Amazon Neptune

**Best For:**
- Relationship-heavy data
- Social networks
- Recommendation engines
- Fraud detection

**Neo4j:**
- Native graph storage
- Cypher query language
- ACID transactions
- Fast relationship traversal

### 6. Time-Series Databases

**Examples:** InfluxDB, TimescaleDB, Prometheus

**Best For:**
- Metrics and monitoring
- IoT sensor data
- Financial tick data
- Log aggregation

**InfluxDB:**
- Optimized for time-series
- Built-in retention policies
- Downsampling
- Continuous queries

### 7. Search Engines

**Examples:** Elasticsearch, Solr

**Best For:**
- Full-text search
- Log analysis
- Analytics
- Near real-time search

**Elasticsearch:**
- Distributed search
- RESTful API
- Aggregations
- Near real-time indexing

### 8. NewSQL

**Examples:** CockroachDB, TiDB, Google Spanner

**Best For:**
- Need SQL with horizontal scaling
- Global distribution
- Strong consistency at scale

**CockroachDB:**
- PostgreSQL-compatible
- Horizontal scalability
- Strong consistency
- Geo-partitioning

---

## Schema Design Patterns

### 1. One-to-One Relationship

**Use Case:** User profile, user settings

```sql
CREATE TABLE users (
    id INT PRIMARY KEY,
    email VARCHAR(255)
);

CREATE TABLE user_profiles (
    user_id INT PRIMARY KEY,
    bio TEXT,
    avatar_url VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Alternative (denormalized):** Combine into single table if always accessed together.

### 2. One-to-Many Relationship

**Use Case:** User → Orders, Blog → Comments

```sql
CREATE TABLE blogs (
    id INT PRIMARY KEY,
    title VARCHAR(255)
);

CREATE TABLE comments (
    id INT PRIMARY KEY,
    blog_id INT,
    content TEXT,
    FOREIGN KEY (blog_id) REFERENCES blogs(id)
);
```

### 3. Many-to-Many Relationship

**Use Case:** Students ↔ Courses, Users ↔ Roles

```sql
CREATE TABLE students (
    id INT PRIMARY KEY,
    name VARCHAR(100)
);

CREATE TABLE courses (
    id INT PRIMARY KEY,
    title VARCHAR(255)
);

-- Junction table
CREATE TABLE student_courses (
    student_id INT,
    course_id INT,
    enrolled_date DATE,
    PRIMARY KEY (student_id, course_id),
    FOREIGN KEY (student_id) REFERENCES students(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
);
```

### 4. Self-Referencing Relationship

**Use Case:** Employee hierarchy, comment threads

```sql
CREATE TABLE employees (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    manager_id INT,
    FOREIGN KEY (manager_id) REFERENCES employees(id)
);
```

### 5. Polymorphic Associations

**Use Case:** Comments on multiple entity types (posts, photos, videos)

**Approach 1: Single table with type column**
```sql
CREATE TABLE comments (
    id INT PRIMARY KEY,
    commentable_type VARCHAR(50),  -- 'post', 'photo', 'video'
    commentable_id INT,
    content TEXT
);
```

**Approach 2: Multiple foreign keys**
```sql
CREATE TABLE comments (
    id INT PRIMARY KEY,
    post_id INT,
    photo_id INT,
    video_id INT,
    content TEXT,
    CHECK (
        (post_id IS NOT NULL AND photo_id IS NULL AND video_id IS NULL) OR
        (post_id IS NULL AND photo_id IS NOT NULL AND video_id IS NULL) OR
        (post_id IS NULL AND photo_id IS NULL AND video_id IS NOT NULL)
    )
);
```

### 6. Inheritance (Table-per-Type)

**Use Case:** Product variants, user types

```sql
-- Base table
CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(255),
    price DECIMAL
);

-- Specific type tables
CREATE TABLE books (
    product_id INT PRIMARY KEY,
    author VARCHAR(100),
    isbn VARCHAR(20),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE electronics (
    product_id INT PRIMARY KEY,
    warranty_months INT,
    brand VARCHAR(100),
    FOREIGN KEY (product_id) REFERENCES products(id)
);
```

### 7. Soft Deletes

**Use Case:** Retain deleted data for auditing

```sql
CREATE TABLE posts (
    id INT PRIMARY KEY,
    title VARCHAR(255),
    deleted_at TIMESTAMP NULL
);

-- Query only active records
SELECT * FROM posts WHERE deleted_at IS NULL;
```

### 8. Audit Trail

**Use Case:** Track changes for compliance

```sql
CREATE TABLE posts (
    id INT PRIMARY KEY,
    title VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by INT,
    updated_by INT
);

-- Or dedicated audit table
CREATE TABLE audit_log (
    id BIGINT PRIMARY KEY,
    table_name VARCHAR(100),
    record_id INT,
    action VARCHAR(10),  -- 'INSERT', 'UPDATE', 'DELETE'
    old_values JSON,
    new_values JSON,
    user_id INT,
    timestamp TIMESTAMP DEFAULT NOW()
);
```

### 9. Multi-Tenancy

**Approach 1: Shared database, shared schema**
```sql
CREATE TABLE tenants (
    id INT PRIMARY KEY,
    name VARCHAR(255)
);

CREATE TABLE users (
    id INT PRIMARY KEY,
    tenant_id INT,
    email VARCHAR(255),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- Always filter by tenant_id
SELECT * FROM users WHERE tenant_id = 123;
```

**Approach 2: Shared database, separate schemas**
```sql
-- PostgreSQL
CREATE SCHEMA tenant_123;
CREATE TABLE tenant_123.users (...);

CREATE SCHEMA tenant_456;
CREATE TABLE tenant_456.users (...);
```

**Approach 3: Separate databases per tenant**
- Complete isolation
- Easier to migrate/backup individual tenants

### 10. JSON/JSONB Columns (PostgreSQL)

**Use Case:** Flexible attributes, semi-structured data

```sql
CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(255),
    attributes JSONB
);

INSERT INTO products (id, name, attributes)
VALUES (1, 'Laptop', '{"cpu": "Intel i7", "ram": "16GB", "storage": "512GB SSD"}');

-- Query JSON
SELECT * FROM products WHERE attributes->>'cpu' = 'Intel i7';

-- Index JSON field
CREATE INDEX idx_attributes_cpu ON products((attributes->>'cpu'));
```

---

## Scaling Strategies

### 1. Vertical Scaling (Scale-Up)

**Approach:** Increase server resources (CPU, RAM, SSD)

**Pros:**
- Simple (no code changes)
- No data distribution complexity
- Strong consistency maintained

**Cons:**
- Limited by hardware
- Expensive at high end
- Single point of failure

**Use Case:** Small to medium applications

### 2. Horizontal Scaling (Scale-Out)

**Approach:** Add more servers

**Techniques:**
- Read replicas (scale reads)
- Sharding (scale writes)
- Connection pooling
- Load balancing

**Pros:**
- Virtually unlimited scaling
- Fault tolerance
- Cost-effective (commodity hardware)

**Cons:**
- Increased complexity
- Distributed system challenges
- Eventual consistency

**Use Case:** Large-scale applications

### 3. Read Replicas

**Use Case:** Read-heavy workloads

```
         Master (Write)
           |
    +------+------+
    |      |      |
  Replica Replica Replica
  (Read)  (Read)  (Read)
```

**Implementation:**
```python
# Route based on operation
if operation == 'read':
    db = read_replica_pool.get_connection()
else:
    db = master.get_connection()
```

### 4. Caching Layer

**Use Case:** Reduce database load

```
Application → Cache (Redis) → Database
```

**Strategies:**
- Cache-aside
- Write-through
- Write-behind

(See [Cache.md](Cache.md) for details)

### 5. Connection Pooling

**Problem:** Opening database connections is expensive.

**Solution:** Reuse connections via pool.

```python
from sqlalchemy import create_engine
engine = create_engine(
    'postgresql://user:pass@host/db',
    pool_size=20,
    max_overflow=10
)
```

**Configuration:**
- **pool_size:** Persistent connections
- **max_overflow:** Additional connections under load

### 6. Materialized Views

**Use Case:** Expensive queries executed frequently

```sql
-- Create materialized view (precomputed results)
CREATE MATERIALIZED VIEW monthly_sales AS
SELECT DATE_TRUNC('month', order_date) AS month,
       SUM(total) AS total_sales
FROM orders
GROUP BY month;

-- Refresh periodically
REFRESH MATERIALIZED VIEW monthly_sales;

-- Query view (fast)
SELECT * FROM monthly_views WHERE month = '2026-05-01';
```

### 7. Archiving Old Data

**Use Case:** Keep database size manageable

**Strategies:**
- Archive to cold storage (S3, Glacier)
- Separate historical database
- Drop old partitions

```sql
-- Drop old partition
DROP TABLE orders_2020;
```

### 8. Database Federation

**Approach:** Split data by function across databases

```
Users DB    ←→ Application ←→ Orders DB
                   ↕
              Products DB
```

**Pros:**
- Specialization (different schemas, engines)
- Fault isolation
- Scales independently

**Cons:**
- Cross-database queries difficult
- Distributed transactions
- Operational complexity

---

## Backup & Recovery

### Backup Types

#### 1. Full Backup

- Complete copy of database
- Longest backup time
- Fastest restore
- Run periodically (daily, weekly)

```bash
# PostgreSQL
pg_dump dbname > backup.sql

# MySQL
mysqldump --all-databases > backup.sql
```

#### 2. Incremental Backup

- Only changes since last backup
- Faster backup
- Slower restore (need full + all incrementals)

```bash
# PostgreSQL WAL archiving
archive_mode = on
archive_command = 'cp %p /archive/%f'
```

#### 3. Differential Backup

- Changes since last full backup
- Compromise between full and incremental

#### 4. Continuous Backup (Point-in-Time Recovery)

- Real-time replication of transaction logs
- Restore to any point in time

```sql
-- PostgreSQL: Restore to specific timestamp
restore_command = 'cp /archive/%f %p'
recovery_target_time = '2026-05-07 12:30:00'
```

### Backup Strategies

**3-2-1 Rule:**
- **3** copies of data
- **2** different media types
- **1** off-site copy

**Frequency:**
- Critical data: Continuous backup
- Transactional data: Daily full + hourly incremental
- Analytics data: Weekly

**Automation:**
- Scheduled backups (cron, cloud schedulers)
- Verify backups regularly
- Test restore procedures

### Recovery Objectives

**RTO (Recovery Time Objective):** Maximum acceptable downtime

**RPO (Recovery Point Objective):** Maximum acceptable data loss

| RTO/RPO | Strategy |
|---------|----------|
| Minutes / Seconds | Hot standby, synchronous replication |
| Hours / Minutes | Warm standby, asynchronous replication |
| Days / Hours | Cold backup, restore from archive |

---

## Anti-Patterns & Pitfalls

### 1. ❌ No Indexes on Foreign Keys

**Problem:** Slow JOINs, cascading deletes

```sql
-- Bad
CREATE TABLE orders (
    id INT PRIMARY KEY,
    customer_id INT  -- No index!
);

-- Good
CREATE INDEX idx_customer_id ON orders(customer_id);
```

### 2. ❌ ENUM Columns

**Problem:** Schema change required to add values

```sql
-- Bad
CREATE TABLE orders (
    status ENUM('pending', 'shipped', 'delivered')
);
-- Adding 'cancelled' requires ALTER TABLE

-- Good: Separate lookup table
CREATE TABLE order_statuses (
    id INT PRIMARY KEY,
    name VARCHAR(50)
);
```

### 3. ❌ Storing Files in Database

**Problem:** Database bloat, performance degradation

```sql
-- Bad
CREATE TABLE photos (
    id INT PRIMARY KEY,
    image BLOB  -- Large binary data
);

-- Good: Store path, files on object storage (S3)
CREATE TABLE photos (
    id INT PRIMARY KEY,
    image_url VARCHAR(255)  -- 's3://bucket/photo.jpg'
);
```

### 4. ❌ Using VARCHAR(255) Everywhere

**Problem:** Wastes space

```sql
-- Bad
CREATE TABLE users (
    email VARCHAR(255),  -- Emails rarely > 100 chars
    country_code VARCHAR(255)  -- Only need 2 chars!
);

-- Good
CREATE TABLE users (
    email VARCHAR(100),
    country_code CHAR(2)
);
```

### 5. ❌ Not Using Transactions

**Problem:** Partial updates, inconsistent state

```python
# Bad
db.execute("UPDATE accounts SET balance = balance - 100 WHERE id = 1")
# Crash here → first account debited, second not credited!
db.execute("UPDATE accounts SET balance = balance + 100 WHERE id = 2")

# Good
db.begin_transaction()
try:
    db.execute("UPDATE accounts SET balance = balance - 100 WHERE id = 1")
    db.execute("UPDATE accounts SET balance = balance + 100 WHERE id = 2")
    db.commit()
except:
    db.rollback()
```

### 6. ❌ Premature Optimization

**Problem:** Over-engineering before understanding access patterns

**Example:** Sharding before hitting single-server limits

**Solution:** Start simple, measure, then optimize.

### 7. ❌ Not Monitoring Slow Queries

**Problem:** Performance degradation unnoticed

**Solution:** Enable slow query log

```sql
-- MySQL
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 2;  -- Log queries > 2 seconds
```

### 8. ❌ Ignoring Connection Limits

**Problem:** "Too many connections" errors

**Solution:** Connection pooling, appropriate limits

```sql
-- PostgreSQL
SHOW max_connections;  -- Check limit
ALTER SYSTEM SET max_connections = 200;
```

### 9. ❌ Lack of Data Validation

**Problem:** Garbage data, hard to query

**Solution:** Constraints, application validation

```sql
CREATE TABLE users (
    email VARCHAR(100) NOT NULL CHECK (email LIKE '%@%'),
    age INT CHECK (age >= 0 AND age <= 150)
);
```

### 10. ❌ Not Planning for Growth

**Problem:** Hit limits unexpectedly

**Solution:**
- Choose scalable architecture upfront
- Monitor growth metrics
- Capacity planning

---

## Real-World Examples

### 1. **Facebook (MySQL)**

**Challenges:**
- Billions of users
- Massive read/write throughput

**Solutions:**
- Extensive sharding
- Read replicas (multiple tiers)
- Custom MySQL patches
- Caching layer (Memcached, TAO)
- Eventual consistency accepted

### 2. **Netflix (Cassandra)**

**Challenges:**
- Global scale
- High availability required
- Time-series data (viewing history)

**Solutions:**
- Multi-region Cassandra clusters
- Eventual consistency
- No single point of failure
- Tuned for write-heavy workload

### 3. **Uber (PostgreSQL + MySQL)**

**Challenges:**
- Real-time location data
- ACID transactions (payments)
- High write throughput

**Solutions:**
- Sharding (Schemaless architecture)
- Ring-pop consistent hashing
- PostgreSQL for transactional data
- MySQL for other workloads

### 4. **Twitter (Manhattan)**

**Challenges:**
- Millions of tweets per day
- Real-time timeline generation
- Global distribution

**Solutions:**
- Custom distributed database (Manhattan)
- Sharding by user ID
- Read-through caching
- Eventually consistent replication

### 5. **Airbnb (MySQL)**

**Challenges:**
- Complex transactions (bookings)
- Data consistency critical
- International expansion

**Solutions:**
- Sharding by region
- Read replicas per region
- Vitess for sharding orchestration
- Database migrations with pt-online-schema-change

### 6. **LinkedIn (Espresso)**

**Challenges:**
- Social graph
- Real-time updates
- Diverse data models

**Solutions:**
- Custom NoSQL (Espresso)
- Timeline consistency (Vector clocks)
- Sharding by user ID
- Voldemort (key-value store) for caching

### 7. **Instagram (PostgreSQL)**

**Challenges:**
- Billions of photos/videos
- Rapid growth
- User experience critical

**Solutions:**
- Aggressive sharding
- ID generation (UUID + timestamp)
- Metadata in PostgreSQL
- Media on object storage (S3)
- Cassandra for feeds

---

## Summary & Decision Tree

### Choosing a Database

```
Start
  |
  Need transactions? ─── Yes ──► SQL (PostgreSQL, MySQL)
  |
  No
  |
  Data structure?
    ├─ Key-Value ──► Redis, DynamoDB
    ├─ Document ──► MongoDB, Couchbase
    ├─ Graph ──► Neo4j, Neptune
    ├─ Time-Series ──► InfluxDB, TimescaleDB
    └─ Column-Family ──► Cassandra, HBase
```

### Scaling Decision Tree

```
Performance issue?
  |
  Measure: Is it database?
  |
  Yes ─► What's the bottleneck?
    ├─ Slow queries ──► Add indexes, optimize queries
    ├─ Too many reads ──► Add read replicas, caching
    ├─ Too many writes ──► Vertical scale, then shard
    ├─ Storage growing ──► Archive old data, partitioning
    └─ Complex queries ──► Materialized views, denormalization
```

### Key Principles

1. **Understand your access patterns** before choosing database/schema
2. **Start simple** (single server, normalized schema)
3. **Measure before optimizing** (don't guess)
4. **Indexes are critical** for read performance
5. **Sharding is complex** (delay as long as possible)
6. **Replication != Backup** (need both)
7. **Test your backups** (restore regularly)
8. **Monitor continuously** (slow queries, replication lag, disk usage)
9. **Choose consistency model explicitly** (ACID vs eventual)
10. **Plan for failure** (replicas, backups, disaster recovery)

### Common Patterns

**Small Application (< 1K users):**
- Single PostgreSQL/MySQL server
- Regular backups
- Vertical scaling as needed

**Medium Application (< 100K users):**
- Primary + read replicas
- Connection pooling
- Caching layer (Redis)
- Automated backups

**Large Application (> 100K users):**
- Sharding or NoSQL
- Multi-region replication
- Sophisticated caching
- Database federation
- Monitoring and alerting

---

## Quick Reference

### SQL Cheat Sheet

```sql
-- Create table
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Add index
CREATE INDEX idx_email ON users(email);

-- Foreign key
ALTER TABLE orders ADD CONSTRAINT fk_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id);

-- Explain query
EXPLAIN ANALYZE SELECT * FROM users WHERE email = 'test@example.com';

-- Transaction
BEGIN;
    UPDATE accounts SET balance = balance - 100 WHERE id = 1;
    UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;

-- Backup
pg_dump dbname > backup.sql

-- Restore
psql dbname < backup.sql
```

### NoSQL Cheat Sheet (MongoDB)

```javascript
// Insert
db.users.insertOne({name: "John", email: "john@example.com"})

// Find
db.users.find({email: "john@example.com"})

// Index
db.users.createIndex({email: 1})

// Update
db.users.updateOne(
    {email: "john@example.com"},
    {$set: {name: "John Doe"}}
)

// Aggregate
db.orders.aggregate([
    {$match: {status: "completed"}},
    {$group: {_id: "$customer_id", total: {$sum: "$amount"}}}
])
```

---
