# 🧠 Key High-Level Design (HLD) Concepts

A concise guide to the **core concepts required for System Design Interviews**.

---

## 🏗️ 1. System Design Basics
- Functional Requirements (What system should do)
- Non-Functional Requirements (Scalability, Reliability, Latency)
- Capacity Estimation (users, traffic, storage)

---

## ⚡ 2. Scalability
- Vertical Scaling (Scale-Up)
- Horizontal Scaling (Scale-Out)
- Stateless vs Stateful Services

---

## 🌐 3. Load Balancing
- Distributes traffic across servers
- Types:
  - Round Robin
  - Least Connections
  - IP Hash

---

## 🧩 4. Caching
- Improves performance by storing frequently accessed data
- Types:
  - Client-side cache
  - CDN cache
  - Server-side cache

- Tools:
  - Redis
  - Memcached

---

## 🗄️ 5. Database Design
- SQL vs NoSQL
- Indexing
- Sharding (Horizontal Partitioning)
- Replication

---

## 🔁 6. Consistency vs Availability (CAP Theorem)
- Consistency
- Availability
- Partition Tolerance

📌 You can only fully guarantee 2 out of 3

---

## 📬 7. Messaging Systems
- Queue-based communication
- Pub-Sub model

- Tools:
  - Kafka
  - RabbitMQ

---

## ⚙️ 8. Microservices Architecture
- Small independent services
- Communicate via APIs

📌 Pros:
- Scalability
- Independent deployment

📌 Cons:
- Complexity
- Network latency

---

## 🚪 9. API Gateway
- Single entry point for all clients
- Handles:
  - Authentication
  - Routing
  - Rate limiting

---

## 🚦 10. Rate Limiting
- Controls number of requests
- Algorithms:
  - Token Bucket
  - Leaky Bucket

---

## 📊 11. Monitoring & Logging
- Track system health
- Tools:
  - Prometheus
  - ELK Stack

---

## 🔐 12. Security
- Authentication vs Authorization
- Encryption (HTTPS, TLS)
- OAuth

---

## 📦 13. CDN (Content Delivery Network)
- Delivers content closer to users
- Reduces latency

---

## 🔄 14. Fault Tolerance
- System continues working despite failures
- Techniques:
  - Replication
  - Failover

---

## 🧠 15. Consistent Hashing
- Distributes data across servers
- Minimizes rehashing during scaling

---

## ⏱️ 16. Latency vs Throughput
- Latency: Time per request
- Throughput: Requests per second

---

## 🧱 17. Monolith vs Microservices
- Monolith: Single codebase
- Microservices: Distributed services

---

## 🔁 18. Data Partitioning
- Horizontal Partitioning (Sharding)
- Vertical Partitioning

---

## 🧰 19. Distributed Systems Basics
- Leader Election
- Consensus Algorithms (Raft, Paxos)

---

## 📡 20. Real-Time Systems
- WebSockets
- Long Polling

---

## 🔁 Golden Rule
**Design systems that are scalable, reliable, and maintainable**
