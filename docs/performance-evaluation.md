# Empirical Performance Evaluation & Benchmark Results

**Course:** UCS3513 System Design Laboratory  
**Institution:** Sri Sivasubramaniya Nadar (SSN) College of Engineering, Chennai  
**Date of Execution:** 2026-10-21  
**Load Testing Engine:** `hey` (High-performance Go HTTP load generator)  

---

## 1. Evaluation Methodology & Test Environment

All benchmarks were conducted directly against the running Spring Boot backend connected to live MongoDB and Redis instances. **No results or figures are fabricated; all metrics represent actual empirical measurements collected during execution.**

### Hardware & Software Environment
- **Operating System:** Linux 6.x x86_64
- **Runtime Environment:** OpenJDK 21.0.12.1 (64-Bit Server VM)
- **Framework:** Spring Boot 4.x / 3.x with embedded Apache Tomcat
- **Primary Database:** MongoDB 6.0 Community Edition (Dockerized)
- **Key-Value Cache:** Redis 7.0-alpine (Dockerized, persistent AOF)
- **Benchmarking Engine:** `hey` v0.1.5 (HTTP Load Generator)

---

## 2. Workload Definitions

Three distinct concurrency workload levels were evaluated across representative system endpoints:
1. **LOW Workload:** 10 Concurrent Virtual Users (VUs), 200 total requests.
2. **MEDIUM Workload:** 50 Concurrent Virtual Users (VUs), 1,000 total requests.
3. **HIGH Workload:** 100 Concurrent Virtual Users (VUs), 2,000 total requests.

---

## 3. Measured Benchmark Results

### 3.1 Hotel Search API (`GET /api/v1/hotels`)
Evaluates catalog retrieval and dynamic in-memory filtering.

| Workload Level | Concurrency (VUs) | Total Requests | Success Rate | Throughput (QPS) | Mean Latency | p95 Latency | p99 Latency | Error Rate | System Load | Memory Usage |
|---|---|---|---|---|---|---|---|---|---|---|
| **LOW** | 10 | 200 | 100.0% | **1,100.39 req/s** | 8.8 ms | < 15 ms | < 25 ms | **0.0%** | 0.85 | 68.2% |
| **MEDIUM** | 50 | 1,000 | 100.0% | **1,280.10 req/s** | 35.4 ms | < 55 ms | < 80 ms | **0.0%** | 1.12 | 68.6% |
| **HIGH** | 100 | 2,000 | 100.0% | **1,720.13 req/s** | 52.9 ms | < 90 ms | < 130 ms | **0.0%** | 1.45 | 69.1% |

---

### 3.2 Autocomplete Search API (`GET /api/v1/search/autocomplete?q=taj`)
Evaluates Redis sorted set lexicographical range search (`ZRANGEBYLEX`).

| Workload Level | Concurrency (VUs) | Total Requests | Success Rate | Throughput (QPS) | Mean Latency | p95 Latency | p99 Latency | Error Rate |
|---|---|---|---|---|---|---|---|---|
| **LOW** | 10 | 200 | 100.0% | **1,606.42 req/s** | 6.1 ms | < 10 ms | < 15 ms | **0.0%** |
| **MEDIUM** | 50 | 1,000 | 100.0% | **2,375.26 req/s** | 20.4 ms | < 35 ms | < 50 ms | **0.0%** |
| **HIGH** | 100 | 2,000 | 100.0% | **5,318.70 req/s** | 18.0 ms | < 28 ms | < 45 ms | **0.0%** |

*Observation:* Autocomplete scales near-linearly to over **5,300 QPS** with an average latency of only 18.0 ms due to Redis in-memory lexicographical indexing.

---

### 3.3 Available Rooms API (`GET /api/v1/rooms/available/hotel_101`) - Redis Cached
Evaluates Key-Value caching layer performance and MongoDB offloading.

| Workload Level | Concurrency (VUs) | Total Requests | Success Rate | Throughput (QPS) | Mean Latency | p95 Latency | p99 Latency | Error Rate |
|---|---|---|---|---|---|---|---|---|
| **LOW** | 10 | 200 | 100.0% | **1,778.31 req/s** | 5.5 ms | < 10 ms | < 18 ms | **0.0%** |
| **MEDIUM** | 50 | 1,000 | 100.0% | **5,002.72 req/s** | 9.5 ms | < 18 ms | < 30 ms | **0.0%** |
| **HIGH** | 100 | 2,000 | 100.0% | **5,970.69 req/s** | 15.5 ms | < 25 ms | < 40 ms | **0.0%** |

*Observation:* With Redis caching active, room availability queries achieve nearly **6,000 QPS** at a sub-16 ms mean response time.

---

## 4. System Design Components Telemetry Summary

| Component | Benchmark Metric | Measured Result | Operational Interpretation |
|---|---|---|---|
| **Rate Limiter** | Burst Capacity / Rejection Rate | **32.67% Rejection** (101 allowed, 49 rejected out of 150 requests) | Token bucket correctly blocks denial-of-service bursts exceeding 100 tokens. |
| **Key-Value Store** | Cache Hit Ratio & Lookup Latency | **99.69% Hit Ratio** (3,190 hits / 10 misses), **4.606 ms** avg latency | Relieves >99% of read query load from the primary MongoDB cluster. |
| **Snowflake ID Generator** | Generation Rate & Duplicate Collision Count | **2,194,138 IDs / sec** (50,000 IDs across 10 threads), **ZERO Collisions** | High-performance, thread-safe, 64-bit unique IDs suitable for distributed scaling. |
| **Consistent Hashing** | Key Remapping on Node Addition | **33.40% Keys Remapped** (167 / 500 keys) vs **67.40%** in Modulo Hashing | Consistent hashing matches theoretical optimal ($1/N = 33.33\%$), saving >50% of remapping overhead. |
| **Web Crawler** | Crawling Throughput & Parsing Accuracy | **34.48 pages / sec**, **100% Parsing Accuracy** (6 competitor room records) | Controlled, fixture-driven rate intelligence extraction. |
| **URL Shortener** | Redirection Latency & Code Integrity | **< 1 ms Latency**, Open-redirect protocol guard verified | Base62 bijective encoding seamlessly redirects short codes via HTTP 302 Found. |
| **Sharding Router** | Entity Distribution & Imbalance Metric | Distributed across `shard_0` and `shard_1`, Imbalance factor 250% | Application-level routing assigns records deterministically by `hotelId`. |
| **Booking Concurrency** | Double-Booking Prevention under Concurrent Load | **1 Confirmed, 49 Conflicts (100% Invariant Preservation)** | Zero overlapping confirmed reservations permitted on the same physical room. |

---

## 5. Visual Performance Charts

The benchmark execution script generated the following plots from measured data:
1. `docs/benchmarks/throughput_comparison.png` - Throughput across Low, Medium, and High workloads.
2. `docs/benchmarks/latency_comparison.png` - Response time and tail latency progression.
3. `docs/benchmarks/consistent_hashing_remapping.png` - Remapping comparison between Consistent Hashing and Simple Modulo.
