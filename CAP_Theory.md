# CAP Theorem: A Complete Technical Guide

## Table of Contents
1. [Fundamentals](#fundamentals)
2. [The Three Properties](#the-three-properties)
3. [Trade-off Analysis](#trade-off-analysis)
4. [CAP Categories](#cap-categories)
5. [Real-World Systems](#real-world-systems)
6. [PACELC Theorem](#pacelc-theorem)
7. [Consistency Models](#consistency-models)
8. [Implementation Patterns](#implementation-patterns)
9. [Partition Handling](#partition-handling)
10. [Decision Framework](#decision-framework)

---

## Fundamentals

### What is CAP Theorem?

**CAP Theorem** (Brewer's Theorem) states that a distributed data store can provide at most **two out of three** guarantees simultaneously:

- **Consistency (C)**: All nodes see the same data at the same time
- **Availability (A)**: Every request receives a response (success or failure)
- **Partition Tolerance (P)**: System continues operating despite network partitions

**Formulated by**: Eric Brewer (2000), proven by Seth Gilbert and Nancy Lynch (2002)

### Core Constraint

```
CAP: Choose 2 out of 3
- CA: Consistency + Availability (no partition tolerance)
- CP: Consistency + Partition Tolerance
- AP: Availability + Partition Tolerance
```

**Reality**: Network partitions are inevitable in distributed systems, so the practical choice is between **CP or AP**.

### Why CAP Matters

1. **Design Decisions**: Guides architecture choices for distributed systems
2. **Trade-off Awareness**: No free lunch - understand what you're sacrificing
3. **Failure Handling**: Defines behavior during network partitions
4. **SLA Definition**: Helps set realistic service level agreements

---

## The Three Properties

### 1. Consistency

**Definition**: All nodes return the same, most recent write for a given piece of data. Every read receives the most recent write or an error.

**Characteristics:**
- Linear consistency (linearizability)
- Single system image
- No stale reads
- Atomic operations across nodes

**Example:**
```
Time: T0 → T1 → T2 → T3
Node A: Write(x=1) → x=1 → x=1 → x=1
Node B: x=0 → x=1 → x=1 → x=1
Client: Read() → 1 (always latest)

Consistent: All nodes synchronized before responding
```

**Implementation Mechanisms:**
- **Two-Phase Commit (2PC)**: Coordinator ensures all nodes commit or abort
- **Paxos/Raft**: Consensus algorithms for distributed agreement
- **Synchronous Replication**: Wait for all replicas before acknowledging write
- **Quorum Writes**: W + R > N (write + read > total nodes)

**Cost:**
- Higher latency (wait for synchronization)
- Reduced availability (reject requests during sync)
- Coordination overhead

### 2. Availability

**Definition**: Every request to a non-failing node receives a response, without guarantee it contains the most recent write.

**Characteristics:**
- System remains operational
- No timeouts or errors (from functioning nodes)
- May return stale data
- Best-effort service

**Example:**
```
Time: T0 → T1 → T2 → T3
Node A: Write(x=1) → x=1 → x=1 → x=1
Node B: x=0 → x=0 → x=0 → x=1 (async sync)
Client: Read(B) → 0 (stale but available)

Available: Node B responds immediately with current state
```

**Implementation Mechanisms:**
- **Asynchronous Replication**: Don't wait for replicas
- **Optimistic Concurrency**: Accept writes, resolve conflicts later
- **Eventual Consistency**: Guarantee convergence over time
- **Multi-Master**: Multiple nodes accept writes

**Cost:**
- Stale reads possible
- Conflict resolution complexity
- Data inconsistency windows

### 3. Partition Tolerance

**Definition**: System continues to operate despite arbitrary message loss or failure of part of the system due to network partitions.

**Characteristics:**
- Network splits system into isolated groups
- Nodes can't communicate across partition
- Must handle split-brain scenarios
- Recovery after partition heals

**Example:**
```
Before Partition:
[Node A] ←→ [Node B] ←→ [Node C]
   All nodes communicate

During Partition:
[Node A] ←→ [Node B]  |  [Node C]
   Network partition separates C

System continues operating despite partition
```

**Partition Causes:**
- Network cable cut
- Router/switch failure
- Firewall rules
- Network congestion
- Geographic network issues
- Configuration errors

**Reality**: Partitions are inevitable in distributed systems, so P is mandatory.

---

## Trade-off Analysis

### CA Systems (Consistency + Availability)

**Characteristics:**
- Prioritize consistency and availability
- No partition tolerance
- Typically single-node or tightly-coupled systems
- Fail completely during network partitions

**Examples:**
- **Single-Server RDBMS**: PostgreSQL, MySQL (single instance)
- **Two-Phase Commit Systems**: Distributed transactions without partition handling

**Limitations:**
```
Problem: Network partition occurs
Outcome: System becomes unavailable (violates A)
        OR Returns inconsistent data (violates C)
```

**Reality**: True CA systems don't exist in distributed environments because network partitions are inevitable.

### CP Systems (Consistency + Partition Tolerance)

**Characteristics:**
- Guarantee consistency during partitions
- Sacrifice availability
- Reject requests during partition to maintain consistency
- Return errors rather than stale data

**Behavior During Partition:**
```
Partition: [Node A, Node B] | [Node C]

Write to minority (C): REJECTED
Read from minority (C): REJECTED or STALE-ERROR

Only majority partition serves requests
Minority becomes unavailable
```

**Trade-offs:**
- ✅ No stale reads
- ✅ Data integrity maintained
- ✅ Predictable behavior
- ❌ Reduced availability during partitions
- ❌ Higher latency (consensus protocols)
- ❌ Some nodes unusable during split

**Implementation Patterns:**
- **Quorum-based**: Require majority for operations
- **Leader Election**: Single leader serves requests
- **Pessimistic Locking**: Block conflicting operations

**Use Cases:**
- **Financial Systems**: Banking, payment processing
- **Inventory Management**: Stock levels, reservations
- **Configuration Systems**: Service discovery, coordination
- **Critical Data**: Medical records, legal documents

**Examples:**
- **HBase**: Consistency over availability
- **MongoDB**: Strong consistency with replica sets
- **Redis Cluster**: Consistency mode
- **ZooKeeper**: Coordination service
- **etcd**: Distributed key-value store
- **Consul**: Service mesh configuration

### AP Systems (Availability + Partition Tolerance)

**Characteristics:**
- Guarantee availability during partitions
- Sacrifice consistency
- Accept writes on both sides of partition
- Eventual consistency after partition heals

**Behavior During Partition:**
```
Partition: [Node A, Node B] | [Node C]

Write to A: ACCEPTED (x=1)
Write to C: ACCEPTED (x=2)

Both sides serve requests independently
Conflict resolution after partition heals
```

**Trade-offs:**
- ✅ Always available
- ✅ Low latency
- ✅ High throughput
- ❌ Stale reads possible
- ❌ Conflict resolution required
- ❌ Complex application logic

**Implementation Patterns:**
- **Last-Write-Wins (LWW)**: Use timestamps
- **Version Vectors**: Track causality
- **CRDTs**: Conflict-free replicated data types
- **Application-Level Merge**: Custom conflict resolution

**Use Cases:**
- **Social Media**: Posts, likes, comments
- **Caching**: CDN, distributed cache
- **Analytics**: Event logging, metrics
- **Shopping Carts**: E-commerce (conflicts rare)
- **DNS**: Domain name resolution
- **Session Storage**: User sessions

**Examples:**
- **Cassandra**: Tunable consistency, AP by default
- **DynamoDB**: Eventual consistency default
- **Riak**: AP system with eventual consistency
- **CouchDB**: Multi-master replication
- **Voldemort**: LinkedIn's key-value store

---

## CAP Categories

### CP Systems Deep Dive

#### MongoDB (Configurable)

**Default Behavior:**
```javascript
// Write concern: majority
db.collection.insertOne(
  {user: "john"},
  {writeConcern: {w: "majority", wtimeout: 5000}}
);

// Read preference: primary
db.collection.find({user: "john"}).readPref("primary");
```

**During Partition:**
- Primary in minority: Steps down, cluster becomes read-only
- Primary in majority: Continues serving requests
- Secondary in minority: Cannot serve consistent reads

**Configuration:**
```
CP Mode: readConcern="linearizable", writeConcern="majority"
AP Mode: readConcern="local", writeConcern=1
```

#### HBase

**Architecture:**
- Single HMaster (leader)
- RegionServers host data
- ZooKeeper for coordination

**Partition Behavior:**
- RegionServer loses ZooKeeper connection → stops serving
- Ensures no split-brain
- Availability sacrificed for consistency

#### Redis Cluster (Configurable)

**CP Mode:**
```
WAIT 2 5000  # Wait for 2 replicas, timeout 5s
```

**Behavior:**
- Block writes until replicas acknowledge
- Reject writes if replicas unavailable
- Consistency guaranteed

### AP Systems Deep Dive

#### Cassandra

**Tunable Consistency:**
```sql
-- Write
INSERT INTO users (id, name) VALUES (1, 'John')
USING CONSISTENCY QUORUM;

-- Read
SELECT * FROM users WHERE id = 1
USING CONSISTENCY ONE;
```

**Consistency Levels:**
```
ONE: Any single node (lowest latency, AP)
QUORUM: Majority of nodes (balanced)
ALL: All replicas (highest consistency, CP)
```

**Partition Behavior:**
```
Partition: [DC1: A, B] | [DC2: C]

CONSISTENCY ONE: Both sides serve requests (AP)
CONSISTENCY QUORUM: Requires 2/3 nodes, may fail (CP)
CONSISTENCY ALL: Requires all 3, always fails during partition
```

**Conflict Resolution:**
- Last-Write-Wins (timestamp-based)
- No cross-partition consistency guarantee

#### DynamoDB

**Default: Eventual Consistency**
```javascript
// Eventually consistent read (AP)
dynamodb.getItem({
  TableName: 'users',
  Key: {id: '123'},
  ConsistentRead: false
});

// Strongly consistent read (CP)
dynamodb.getItem({
  TableName: 'users',
  Key: {id: '123'},
  ConsistentRead: true
});
```

**Partition Behavior:**
- Writes always accepted (AP)
- Eventual consistency: May read stale data
- Strong consistency: Read from leader only

#### Riak

**Configuration:**
```
R + W > N  # CP (quorum)
R + W ≤ N  # AP (eventual consistency)

Example: N=3, R=1, W=1 (AP)
Example: N=3, R=2, W=2 (CP)
```

**Vector Clocks:**
```
Write 1: {A:1} → "value1"
Write 2: {B:1} → "value2"  (concurrent)

Read: Returns both versions (sibling values)
Application resolves conflict
```

---

## PACELC Theorem

### Extension of CAP

CAP addresses behavior during partitions, but what about normal operation?

**PACELC**: **P**artition → **A**vailability vs **C**onsistency, **E**lse → **L**atency vs **C**onsistency

```
IF partition (P):
    Trade-off between Availability (A) and Consistency (C)
ELSE (E):
    Trade-off between Latency (L) and Consistency (C)
```

### PACELC Categories

#### PA/EL (Prioritize Availability and Latency)

**Systems**: Cassandra, Riak, CouchDB

**Characteristics:**
- During partition: Remain available (AP)
- Normal operation: Optimize for latency (asynchronous replication)

**Trade-off**: Eventual consistency always

#### PA/EC (Prioritize Availability, but Consistency when no partition)

**Systems**: DynamoDB (with strong consistency)

**Characteristics:**
- During partition: Remain available
- Normal operation: Strong consistency (synchronous replication)

**Trade-off**: Higher latency during normal operation

#### PC/EL (Prioritize Consistency, but Latency when no partition)

**Systems**: MongoDB (rare configuration)

**Characteristics:**
- During partition: Reject requests
- Normal operation: Asynchronous replication

**Trade-off**: Inconsistency during normal operation

#### PC/EC (Prioritize Consistency always)

**Systems**: HBase, Redis Cluster (wait), BigTable

**Characteristics:**
- During partition: Reject requests
- Normal operation: Synchronous replication

**Trade-off**: Highest latency, lowest availability

### Decision Matrix

| System | Partition | Normal Operation | Use Case |
|--------|-----------|------------------|----------|
| Cassandra | AP | EL | Social media, IoT |
| DynamoDB | AP | EC | E-commerce |
| MongoDB | CP | EL | Content management |
| HBase | CP | EC | Financial systems |

---

## Consistency Models

### Strong Consistency

**Linearizability**: Operations appear instantaneous and in global order

```
Timeline:
T1: Client A writes x=1
T2: Client B reads x → Returns 1 (never 0 after T1)
T3: Client C reads x → Returns 1
```

**Implementation:**
- Synchronous replication
- Consensus protocols (Paxos, Raft)
- Distributed locks

**Latency**: High (wait for consensus)

### Sequential Consistency

**Definition**: Operations appear in some sequential order consistent with program order on each node

```
Node A order: Write x=1, Write y=2
Node B order: Read y=2, Read x=1

Valid: All nodes see writes in same order
```

**Relaxed**: Global timestamp not required

### Causal Consistency

**Definition**: Operations with causal relationship seen by all nodes in same order

```
Post message: "Hello" (T1)
Reply to message: "Hi" (T2, depends on T1)

All nodes see: "Hello" before "Hi"
Independent posts: Can appear in any order
```

**Implementation**: Vector clocks, version vectors

### Eventual Consistency

**Definition**: If no new updates, all replicas eventually converge to same value

```
T1: Write x=1 to Node A
T2: Read x from Node B → Returns 0 (stale)
T3: (later) Read x from Node B → Returns 1 (eventually consistent)
```

**Guarantees:**
- Read-your-writes: Client sees own writes immediately
- Monotonic reads: Once seen value v, never see older value
- Monotonic writes: Writes by same client applied in order

### Weak Consistency

**Definition**: No guarantees about when updates become visible

**Use Cases**: Real-time systems (video conferencing, gaming)

---

## Implementation Patterns

### Quorum-Based Systems

**Formula:**
```
N = Total replicas
W = Write quorum (nodes to acknowledge write)
R = Read quorum (nodes to query for read)

Strong Consistency: W + R > N
Weak Consistency: W + R ≤ N
```

**Examples:**
```
N=3, W=2, R=2 → W+R=4 > 3 (Strong consistency)
N=3, W=1, R=1 → W+R=2 ≤ 3 (Eventual consistency)

N=5, W=3, R=3 → W+R=6 > 5 (Tolerate 2 node failures)
```

**Trade-offs:**
```
High W, High R:
  + Strong consistency
  - High latency
  - Lower availability

Low W, Low R:
  + Low latency
  + High availability
  - Eventual consistency
```

### Implementation Example

```java
public class QuorumStore {
    private List<Node> nodes;
    private int N;
    private int W;
    private int R;
    
    public QuorumStore(List<Node> nodes, int W, int R) {
        this.nodes = nodes;
        this.N = nodes.size();
        this.W = W;
        this.R = R;
    }
    
    public boolean write(String key, String value) {
        int acks = 0;
        long timestamp = System.currentTimeMillis();
        
        for (Node node : nodes) {
            try {
                node.write(key, value, timestamp);
                acks++;
                if (acks >= W) {
                    return true;  // Success
                }
            } catch (Exception e) {
                continue;
            }
        }
        return false;  // Failed to reach quorum
    }
    
    public String read(String key) throws Exception {
        List<Response> responses = new ArrayList<>();
        
        for (Node node : nodes) {
            try {
                Response response = node.read(key);
                responses.add(response);
                if (responses.size() >= R) {
                    break;
                }
            } catch (Exception e) {
                continue;
            }
        }
        
        if (responses.size() < R) {
            throw new Exception("Read quorum not met");
        }
        
        // Return value with latest timestamp
        return responses.stream()
            .max(Comparator.comparing(Response::getTimestamp))
            .map(Response::getValue)
            .orElse(null);
    }
}
```

### Consensus Algorithms

#### Paxos

**Phases:**
1. **Prepare**: Proposer sends proposal number
2. **Promise**: Acceptors promise not to accept lower proposals
3. **Accept**: Proposer sends value
4. **Accepted**: Acceptors accept if promise still valid

**Guarantees:**
- Safety: Only one value chosen
- Liveness: Eventually some value chosen (if majority available)

**Complexity**: Difficult to implement correctly

#### Raft

**Simplified consensus algorithm**

**Components:**
- **Leader**: Handles all client requests
- **Followers**: Replicate leader's log
- **Candidates**: Compete for leadership during election

**Log Replication:**
```
1. Client sends command to leader
2. Leader appends to local log
3. Leader replicates to followers
4. Leader waits for majority acknowledgment
5. Leader commits and applies to state machine
6. Leader notifies followers to commit
```

**Partition Handling:**
```
Partition: [Leader + 1 Follower] | [2 Followers]

Majority partition (3/5):
  - Current leader continues (has majority)
  - Accepts writes
  - Consistent

Minority partition (2/5):
  - Followers cannot elect new leader
  - No writes accepted
  - Unavailable (CP)
```

### Vector Clocks

**Track causality in distributed systems**

**Structure:**
```
Vector Clock: [NodeA: 2, NodeB: 1, NodeC: 3]
```

**Operations:**
```java
public class VectorClock {
    private String nodeId;
    private Map<String, Integer> clock;
    
    public VectorClock(String nodeId) {
        this.nodeId = nodeId;
        this.clock = new HashMap<>();
    }
    
    public void increment() {
        clock.put(nodeId, clock.getOrDefault(nodeId, 0) + 1);
    }
    
    public void update(Map<String, Integer> other) {
        for (Map.Entry<String, Integer> entry : other.entrySet()) {
            String node = entry.getKey();
            int count = entry.getValue();
            clock.put(node, Math.max(clock.getOrDefault(node, 0), count));
        }
        increment();
    }
    
    public boolean happensBefore(VectorClock other) {
        // self < other if all entries ≤ and at least one <
        Set<String> allNodes = new HashSet<>(clock.keySet());
        allNodes.addAll(other.clock.keySet());
        
        boolean hasLess = false;
        for (String node : allNodes) {
            int thisCount = clock.getOrDefault(node, 0);
            int otherCount = other.clock.getOrDefault(node, 0);
            
            if (thisCount > otherCount) {
                return false;
            }
            if (thisCount < otherCount) {
                hasLess = true;
            }
        }
        return hasLess;
    }
    
    public boolean concurrent(VectorClock other) {
        return !happensBefore(other) && !other.happensBefore(this);
    }
}
```

**Example:**
```
Initial: A=[A:0], B=[B:0]

Event 1: A writes → A=[A:1]
Event 2: B writes → B=[B:1]
Event 3: A receives B's update → A=[A:2, B:1]
Event 4: B receives A's original → B=[A:1, B:2]

A=[A:2, B:1] vs B=[A:1, B:2]: Concurrent (conflict)
```

---

## Partition Handling

### Detection

**Heartbeat Mechanism:**
```java
public class PartitionDetector {
    private List<String> nodes;
    private long timeout;
    private Map<String, Long> lastHeartbeat;
    
    public PartitionDetector(List<String> nodes, long timeoutSeconds) {
        this.nodes = nodes;
        this.timeout = timeoutSeconds * 1000;
        this.lastHeartbeat = new ConcurrentHashMap<>();
    }
    
    public void sendHeartbeat(String nodeId) {
        lastHeartbeat.put(nodeId, System.currentTimeMillis());
    }
    
    public List<String> checkPartition() {
        long now = System.currentTimeMillis();
        List<String> unreachable = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : lastHeartbeat.entrySet()) {
            String nodeId = entry.getKey();
            long lastSeen = entry.getValue();
            
            if (now - lastSeen > timeout) {
                unreachable.add(nodeId);
            }
        }
        return unreachable;
    }
}
```

### Split-Brain Prevention

**Problem**: Multiple nodes believe they're leader

**Solutions:**

#### 1. Quorum

```java
public boolean canOperate(int reachableNodes, int totalNodes) {
    return reachableNodes > totalNodes / 2.0;
}

// Network partition: 3 nodes split 2-1
int partition1 = 2;  // Can operate (2 > 3/2)
int partition2 = 1;  // Cannot operate (1 ≤ 3/2)
```

#### 2. Fencing

```java
public class Fencing {
    private AtomicLong epoch;
    
    public Fencing() {
        this.epoch = new AtomicLong(0);
    }
    
    public long becomeLeader() {
        return epoch.incrementAndGet();
    }
    
    public boolean validateOperation(long operationEpoch) throws Exception {
        if (operationEpoch < epoch.get()) {
            throw new Exception("Fenced: Stale leader detected");
        }
        return true;
    }
}
```

#### 3. Witness Node

```
3-node cluster + 1 witness (no data)

Partition: [Node A, Witness] | [Node B, Node C]

Partition 1 (with witness): 2 data nodes + 1 witness = Quorum
Partition 2: Cannot form quorum
```

### Recovery

**Partition Heals:**

```java
public void partitionRecovery() {
    // 1. Detect partition healed
    if (allNodesReachable()) {
        // 2. Compare versions
        List<Conflict> conflicts = detectConflicts();
        
        // 3. Resolve conflicts
        for (Conflict conflict : conflicts) {
            Object resolvedValue = resolveConflict(conflict);
            replicateToAll(resolvedValue);
        }
        
        // 4. Sync state
        syncAllNodes();
        
        // 5. Resume normal operation
        setState(State.NORMAL);
    }
}
```

**Conflict Resolution Strategies:**

#### Last-Write-Wins (LWW)
```java
public Value lwwResolve(Value v1, Value v2) {
    return v1.getTimestamp() > v2.getTimestamp() ? v1 : v2;
}
```

#### Application-Level Merge
```java
public ShoppingCart mergeShoppingCarts(ShoppingCart cart1, ShoppingCart cart2) {
    ShoppingCart merged = new ShoppingCart();
    
    // Union of items
    Set<String> allItems = new HashSet<>();
    allItems.addAll(cart1.getItems());
    allItems.addAll(cart2.getItems());
    
    // Resolve quantities by max
    for (String item : allItems) {
        int quantity = Math.max(
            cart1.getQuantity(item),
            cart2.getQuantity(item)
        );
        merged.addItem(item, quantity);
    }
    
    return merged;
}
```

#### CRDTs (Conflict-Free Replicated Data Types)
```java
public class GCounter {
    // Grow-only counter (CRDT)
    private String nodeId;
    private Map<String, Integer> counts;
    
    public GCounter(String nodeId) {
        this.nodeId = nodeId;
        this.counts = new ConcurrentHashMap<>();
    }
    
    public void increment() {
        counts.put(nodeId, counts.getOrDefault(nodeId, 0) + 1);
    }
    
    public int value() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    public void merge(GCounter other) {
        for (Map.Entry<String, Integer> entry : other.counts.entrySet()) {
            String node = entry.getKey();
            int count = entry.getValue();
            counts.put(node, Math.max(counts.getOrDefault(node, 0), count));
        }
    }
}
```

---

## Decision Framework

### Choosing CP vs AP

#### Choose CP (Consistency + Partition Tolerance) When:

**Requirements:**
- ✅ Data correctness critical (financial, inventory)
- ✅ Cannot tolerate stale reads
- ✅ ACID transactions required
- ✅ Regulatory compliance (audit trails)
- ✅ Predictable behavior more important than uptime

**Examples:**
- Banking systems (account balances)
- Stock trading platforms
- Booking systems (hotel rooms, tickets)
- Distributed locks (coordination)
- Configuration management

**Acceptable Trade-offs:**
- Higher latency (consensus overhead)
- Reduced availability during partitions
- Some requests rejected

**Implementation:**
```java
// CP system behavior
public Result writeData(String key, String value) {
    try {
        // Wait for quorum
        int acks = replicateToMajority(key, value, 5000);
        if (acks >= QUORUM) {
            return Result.SUCCESS;
        } else {
            return Result.ERROR;  // Reject write (maintain consistency)
        }
    } catch (TimeoutException e) {
        return Result.ERROR;  // Unavailable, but consistent
    }
}
```

#### Choose AP (Availability + Partition Tolerance) When:

**Requirements:**
- ✅ Uptime critical (99.99%+ availability)
- ✅ Stale reads acceptable
- ✅ Eventual consistency sufficient
- ✅ User experience prioritized
- ✅ Conflicts rare or easily resolved

**Examples:**
- Social media (posts, likes, follows)
- Content delivery (articles, videos)
- Shopping carts (conflicts rare)
- Session storage
- Caching layers
- DNS

**Acceptable Trade-offs:**
- Stale reads possible
- Conflict resolution required
- Complex application logic

**Implementation:**
```java
// AP system behavior
public Result writeData(String key, String value) {
    // Fire-and-forget to local node
    localNode.write(key, value);
    asyncReplicateToOthers(key, value);  // Background
    return Result.SUCCESS;  // Always available
}

public String readData(String key) {
    return localNode.read(key);  // May be stale, but fast
}
```

### Hybrid Approaches

#### Multi-Datacenter Strategy

```
Critical Data (CP):
  - Account balances
  - Inventory counts
  - Synchronous replication within datacenter
  - Cross-datacenter: Strong consistency

Non-Critical Data (AP):
  - User profiles
  - Content
  - Asynchronous replication
  - Eventual consistency
```

#### Per-Operation Tuning

```java
public class TunableStore {
    
    public Result write(String key, String value, ConsistencyLevel consistency) {
        switch (consistency) {
            case STRONG:
                return writeCP(key, value);  // Wait for all
            case QUORUM:
                return writeQuorum(key, value);  // Wait for majority
            default:
                return writeAP(key, value);  // Fire-and-forget
        }
    }
    
    public String read(String key, ConsistencyLevel consistency) {
        if (consistency == ConsistencyLevel.STRONG) {
            return readCP(key);  // Read from leader
        } else {
            return readAP(key);  // Read from any replica
        }
    }
}
```

### Practical Guidelines

#### 1. Analyze Your Data

**Questions:**
- What's the cost of stale data?
- What's the cost of downtime?
- How often do conflicts occur?
- Can conflicts be resolved automatically?

#### 2. Domain-Driven Decisions

```
Financial Transactions → CP (correctness critical)
User Comments → AP (availability critical)
Product Catalog → AP (reads >> writes)
Shopping Cart → AP (merge conflicts rare)
Order Placement → CP (no double-booking)
Recommendations → AP (stale OK)
```

#### 3. Use CAP-Aware Databases

| Database | CAP | Tunable | Best For |
|----------|-----|---------|----------|
| PostgreSQL | CP | No | ACID transactions |
| MongoDB | CP | Yes | Document storage |
| Cassandra | AP | Yes | Wide-column data |
| DynamoDB | AP | Yes | Key-value storage |
| Redis | CP | Yes | Caching, sessions |
| CouchDB | AP | Limited | Offline-first apps |

#### 4. Multi-Model Strategy

```
Application Architecture:
┌─────────────────────────────────┐
│  Application Layer              │
├─────────────────────────────────┤
│  PostgreSQL (CP)                │  ← Critical data
│  - User accounts                │
│  - Transactions                 │
├─────────────────────────────────┤
│  Cassandra (AP)                 │  ← High-volume data
│  - Event logs                   │
│  - Analytics                    │
├─────────────────────────────────┤
│  Redis (CP/AP)                  │  ← Caching
│  - Session storage              │
│  - Rate limiting                │
└─────────────────────────────────┘
```

---

## Summary

### Key Takeaways

1. **CAP is a Trade-off**: Cannot have all three in distributed systems
2. **P is Mandatory**: Network partitions will happen
3. **Choose CP or AP**: Based on business requirements
4. **PACELC Extends CAP**: Consider latency during normal operation
5. **Tunable Consistency**: Modern systems allow per-operation configuration
6. **No Silver Bullet**: Different data needs different guarantees

### Mental Model

```
Network Partition Occurs:
│
├─ Prioritize Consistency (CP)
│  ├─ Reject requests from minority
│  ├─ Wait for partition to heal
│  └─ Guarantee: No stale data
│
└─ Prioritize Availability (AP)
   ├─ Accept requests from all partitions
   ├─ Resolve conflicts after partition heals
   └─ Guarantee: System always responsive
```

### Evolution Beyond CAP

Modern distributed systems recognize:
- **Tunable Consistency**: Choose per operation
- **Regional Consistency**: Strong within region, eventual across regions
- **Hybrid Models**: CP for writes, AP for reads
- **CRDTs**: Conflict-free data structures
- **Compensating Transactions**: Saga pattern for distributed transactions

**Bottom Line**: Understand your requirements, choose appropriate trade-offs, design for failure.
