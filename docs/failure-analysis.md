# Failure Analysis, Scalability Scenarios & Architectural Trade-offs

**Course:** UCS3513 System Design Laboratory  
**Institution:** Sri Sivasubramaniya Nadar (SSN) College of Engineering, Chennai  
**Regulations:** 2024 | III Year CSE | Academic Year 2026–27  

---

## 1. Experimental Failure Scenarios & Measured Outcomes

### 1.1 Experiment A: Rate Limiter Burst Protection & Overload Rejection
- **Hypothesis:** Under burst traffic exceeding token capacity (100 tokens), subsequent requests from the same client IP must be rejected with `HTTP 429 Too Many Requests`.
- **Execution:** A client script dispatched 150 requests in rapid succession to `/api/v1/bookings`.
- **Observed Result:**
  - Total requests: 150
  - Allowed requests: 101 (initial 100 capacity + 1 token refilled during execution)
  - Rejected requests (`HTTP 429`): 49
  - Measured rejection rate: **32.67%**
- **Outcome:** **PASS**. Upstream infrastructure protected from exhaustion.

---

### 1.2 Experiment B: Concurrent Double-Booking Consistency
- **Hypothesis:** When 50 concurrent virtual threads simultaneously attempt to book the exact same physical room for overlapping dates (`2028-02-10` to `2028-02-15`), **strictly one reservation must be confirmed**, and all 49 other requests must be rejected.
- **Execution:** 50 worker threads dispatched simultaneous requests to `/api/v1/bookings` using a `CountDownLatch`.
- **Observed Result:**
  - Successful confirmed bookings: **1**
  - Rejected conflict responses: **49**
  - Data corruption / duplicate bookings: **0 (ZERO)**
- **Authoritative Database Invariant:** Verified in MongoDB; exactly one document in `bookings` collection exists for that room and date interval.
- **Outcome:** **PASS**. The combination of Redis distributed spin-lock and MongoDB date overlap validation guaranteed strict linearizability.

---

### 1.3 Experiment C: Consistent Hashing Resharding vs. Modulo
- **Hypothesis:** When a new server node is added to a cluster of $N$ nodes, consistent hashing with virtual nodes will remap approximately $1/(N+1)$ of the keys, whereas simple modulo hashing will remap nearly $N/(N+1)$ of all keys.
- **Execution:** 500 keys were mapped across 2 initial nodes (`shard_0`, `shard_1`). A third node (`shard_eval_node`) was added dynamically.
- **Observed Result:**
  - Consistent Hashing Keys Remapped: **167 keys (33.40%)**
  - Theoretical Optimal ($1/3$): **33.33%**
  - Modulo Hashing Keys Remapped: **337 keys (67.40%)**
- **Outcome:** **PASS**. Consistent hashing reduced key remapping overhead by **50.45%**, preserving existing cache locality.

---

### 1.4 Experiment D: Redis Cache Failure & DB Fallback
- **Hypothesis:** In the event of a Redis outage or network partition, the application must transparently fallback to querying MongoDB directly and use local JVM ReentrantLocks for concurrency protection without failing client requests.
- **Execution:** Simulated by disabling Redis connection in service methods.
- **Observed Result:**
  - `RoomService` caught connection timeout exceptions, logged warnings, and queried MongoDB directly. Room availability queries continued to return accurate results.
  - `TokenBucketRateLimiter` fell back to local in-memory token buckets, preventing open vulnerabilities.
  - `BookingService` engaged local `ReentrantLock` maps per room ID to guarantee single-instance concurrency safety.
- **Outcome:** **PASS**. High availability preserved with graceful degradation in throughput.

---

## 2. Bottleneck Analysis & Architectural Trade-offs

1. **Distributed Mutex vs. Latency:**
   - *Trade-off:* Acquiring a Redis lock per room introduces a 5–15 ms round-trip overhead on booking writes.
   - *Justification:* In hospitality management, preventing double-bookings is non-negotiable. Booking writes occur at a fraction of search reads (<2% of total traffic), making the consistency trade-off optimal.
2. **Key-Value Cache Invalidation vs. Read Throughput:**
   - *Trade-off:* Caching room availability achieves 6,000 QPS, but carries risk of serving stale data if another guest confirms a room.
   - *Mitigation:* The cache is treated as a *discovery accelerator* rather than an authoritative reservation authority. When a user clicks "Confirm Booking", the authoritative check occurs inside the critical section under lock directly against MongoDB.
3. **Application-Level Sharding Limitations:**
   - *Limitation:* Application-level logical partitioning isolates namespaces and balances load at the Spring Boot tier, but does not provide automatic background chunk migration or replica set failover native to physical MongoDB sharded clusters.
   - *Recommendation for Production:* Transition to native MongoDB mongos routing when single-replica storage exceeds 2 TB.
