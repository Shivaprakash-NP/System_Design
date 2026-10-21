# System Design Component Mapping & Architectural Justification

**Course:** UCS3513 System Design Laboratory  
**Institution:** Sri Sivasubramaniya Nadar (SSN) College of Engineering  
**Academic Year:** 2026–27 | Regulations 2024  
**Project:** Scalable Hotel Management System  

---

## 1. Functionality-to-Component Mapping Table

The following matrix documents the analysis, applicability, justification, and integration status of all **eight core system-design components** specified in the curriculum.

| # | System Design Component | Status in Project | Target System Functionality | Architectural Justification | Algorithmic Implementation Details | Failure Mode & Recovery |
|---|---|---|---|---|---|---|
| **1** | **Rate Limiter** | **Implemented & Integrated** | API Gateway protection (`/api/v1/bookings`, `/auth/login`) | Prevents resource starvation, scraping, and brute-force attacks during flash sales / peak reservations. | **Token Bucket Algorithm via Redis Lua Script** (atomic evaluation). Default 100 req/min per IP, 2 tokens/sec refill rate. In-memory bucket fallback on Redis outage. | Returns `HTTP 429 Too Many Requests` with retry metadata. Transparent fallback to JVM local locks when cache is partitioned. |
| **2** | **Database Sharding** | **Implemented & Integrated** | Partitioning of Rooms, Hotels, and Bookings across logical namespaces (`shard_0`, `shard_1`, `shard_2`) | Overcomes single-node storage and query throughput limits by distributing hotel inventory across multiple namespaces using `hotelId` as Shard Key. | **Application-Level Partitioning** via `ShardingService` and `ConsistentHashRing`. Routes writes and reads deterministically by shard key. Demonstrates dynamic shard addition/removal and rebalancing. | Discloses that application-level partitioning isolates namespaces at the app tier, distinct from native MongoDB mongos/chunk balancer clustering. |
| **3** | **Consistent Hashing** | **Implemented & Integrated** | Distribution of hotels, rooms, and request routing across server nodes | Mitigates cascading cache invalidation and minimizes key remapping during dynamic node addition or server failure compared to modulo hashing. | **MD5 Hash Ring with 100 Virtual Nodes per Physical Node** (`ConsistentHashRing`). Logarithmic `O(log N)` lookup using `ConcurrentSkipListMap`. Measured remapping of 33.4% on node addition (matching theoretical 1/N target), vs 67.4% in modulo. | Virtual nodes prevent hot-spot skew. Node failures shift only adjacent keys on the ring to successor nodes. |
| **4** | **Key-Value Store** | **Implemented & Integrated** | High-speed caching of room availability, hotel metadata, and autocomplete suggestions | Relieves MongoDB read pressure for high-frequency room availability queries. Read-to-write ratio in hotel search exceeds 50:1. | **Redis 7.0 Key-Value Cache** with 10-minute TTL, JSON serialization, and deterministic cache keying (`rooms:available:{hotelId}`). Atomic eviction on booking creation, cancellation, or checkout. | Observed **99.69% Cache Hit Ratio** and **4.6 ms average lookup latency**. Fallback to authoritative MongoDB on cache miss or connection failure. |
| **5** | **Unique ID Generator** | **Implemented & Integrated** | Generation of 64-bit globally unique, time-sortable Booking and Invoice IDs | Eliminates centralized DB auto-increment bottlenecks and UUID storage penalties (128-bit string vs 64-bit integer). | **Twitter Snowflake Algorithm**: 41-bit timestamp (millisecond precision), 5-bit datacenter ID, 5-bit machine ID, and 12-bit sequence number (4,096 IDs/ms/node). Collision-tested at **2.19 Million IDs/sec** across 10 threads with **ZERO collisions**. | Sequence overflow waits for next millisecond. Clock rollback detection throws defensive exception to prevent duplication. |
| **6** | **URL Shortener** | **Implemented & Integrated** | Compact reservation confirmation and invoice payment links sent in SMS/Notifications | Shortens verbose web application URLs into 6–7 character tokens for SMS character budget constraints and clean notifications. | **Base62 Bijective Encoding** (`[a-zA-Z0-9]`) with dual-layer Redis cache and MongoDB persistence. Direct redirection via `GET /r/{shortCode}` with HTTP 302 Found and open-redirect protocol validation. | Cache lookup miss falls back to MongoDB. Non-existent tokens return `HTTP 404 Not Found`. Average redirection latency < 1 ms. |
| **7** | **Web Crawler** | **Implemented & Integrated** | Controlled fixture-driven scraping of competitor hotel room rates and amenities | Enables dynamic competitive rate intelligence and market pricing analytics without polluting primary transactional inventory. | **JSoup Multi-Page HTML Parser** operating on controlled competitor fixtures (`ExternalHotelCrawler`). Extracts competitor titles, room tiers, live prices, amenities, and guest ratings with robots.txt compliance. | Throughput: **34.48 pages/sec**, 100% schema conformance. Distinguishes unauthoritative competitor estimates from primary hotel inventory. |
| **8** | **Autocomplete Engine** | **Implemented & Integrated** | Instantaneous prefix search for hotel names, locations, and room types on the booking interface | Reduces user search friction, accelerates discovery, and lowers database query frequency by resolving prefixes at keystroke speed. | **Redis Sorted Set (ZSet) Lexicographical Range Matching** (`rangeByLex`) over closed intervals `[prefix, prefix+\uffff)`. In-memory **Trie Data Structure** acts as zero-dependency fallback. | Sub-5 millisecond query latency. Debounced at 200 ms on the React frontend to prevent network flooding. |

---

## 2. Invariant & Concurrency Protection Justification

The primary system invariant:
$$\forall r \in \text{Rooms}, \quad \text{Overlapping}(b_1, b_2) \implies (b_1 = b_2) \lor (\text{Status}(b_1) = \text{CANCELLED}) \lor (\text{Status}(b_2) = \text{CANCELLED})$$

- **Mechanism:** Dual-layer concurrency guard:
  1. Distributed Redis Mutex: `lock:room:{roomId}` with a spin-wait lease acquisition window of 3,000 ms.
  2. Authoritative Database Overlap Verification: Confirmed active bookings are checked for half-open interval overlap `[checkIn, checkOut)`.
  3. Reentrant JVM lock fallback ensuring single-process safety if Redis network partitions occur.
- **Verification:** Empirically verified under concurrent load of 50 simultaneous reservation requests; strictly 1 reservation succeeded, and 49 requests received `HTTP 409 Conflict`.
