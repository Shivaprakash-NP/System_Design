# Scalable Hotel Management System

**Academic Course:** UCS3513 System Design Laboratory, III Year CSE  
**Institution:** Sri Sivasubramaniya Nadar (SSN) College of Engineering, Chennai  
**Regulations:** 2024 | Academic Year: 2026–27  

A production-grade, distributed hotel management and room reservation system built to demonstrate and evaluate all **eight system-design assignment components**: Token Bucket Rate Limiting, Application-Level Database Sharding, Consistent Hashing with Virtual Nodes, Redis Key-Value Caching, Twitter Snowflake 64-bit ID Generation, Base62 URL Shortening, Fixture-Driven Web Crawling, and Redis Lexicographical Autocomplete.

---

## 1. Technology Stack

- **Backend:** Java 21, Spring Boot 4.x/3.x, Maven, Spring Security, JWT (HMAC-SHA512), Bean Validation, JSoup.
- **Persistence Tier:** MongoDB 6.0 (Community Edition), Spring Data MongoDB.
- **In-Memory & Caching Tier:** Redis 7.0 (Alpine), Spring Data Redis (Reactive & Lettuce).
- **Frontend:** React 19, JavaScript / TypeScript, Vite build engine, Axios, React Router v7.
- **Load Testing & Verification:** `hey` HTTP engine, JUnit 5 Platform, Python 3 benchmark harness, Matplotlib.
- **Containerization:** Root-level `docker-compose.yml` orchestrating MongoDB, Redis, Spring Boot backend, and Vite frontend.

---

## 2. Seeded Local Demo Accounts

The database automatically seeds local-demo accounts upon application startup:

| Role | Email Address | Password | Intended Workflow |
|---|---|---|---|
| **Guest** | `guest@hotel.demo` | `Guest@12345` | Hotel Search, Live Autocomplete, Room Reservations, Booking History, Invoices |
| **Receptionist** | `reception@hotel.demo` | `Reception@12345` | Front Desk Portal, Guest Check-In, Guest Checkout & Itemized Billing |
| **Housekeeping** | `housekeeping@hotel.demo` | `Housekeep@12345` | Room Sanitization Kanban, Status Transitions (`DIRTY` $\to$ `CLEANING` $\to$ `CLEAN` $\to$ `INSPECTED`) |
| **Administrator** | `admin@hotel.demo` | `Admin@12345` | Unrestricted access across all operational, staff, and system design telemetry portals |

*Note: 1-Click quick fill buttons are available on the Login interface.*

---

## 3. Quick Start & Execution

### Option A: One-Command Docker Compose (Recommended)
```bash
# Clone and enter workspace
git clone <repo-url>
cd SDLAB

# Launch complete stack (MongoDB, Redis, Backend, Frontend)
docker compose up -d --build
```
- Frontend application: **`http://localhost:5173`**
- Backend REST API: **`http://localhost:8080`**
- Health endpoint: **`http://localhost:8080/actuator/health`**

### Option B: Local Development Execution

#### 1. Start Infrastructure Containers
```bash
docker compose up -d redis mongodb
```

#### 2. Build & Launch Backend
```bash
cd backend
mvn clean package -DskipTests
java -jar target/system-0.0.1-SNAPSHOT.jar
```

#### 3. Launch Frontend (In another terminal)
```bash
cd frontend
npm install
npm run build
npx vite preview --port 5173 --host
```

Open **`http://localhost:5173`** in your browser.

---

## 4. System Design Components Implementation

| # | Component | Location | Implementation Details | Measured Metric |
|---|---|---|---|---|
| 1 | **Rate Limiter** | `com.hotel.system.components.ratelimiter` | Token bucket Lua script on Redis. 100 req/min burst, 2 tokens/s refill rate. In-memory fallback. | **32.67% Rejection Rate** under 150-request burst overload. |
| 2 | **Database Sharding** | `com.hotel.system.service.ShardingService` | Application-level entity partitioning across logical namespaces (`shard_0`, `shard_1`). | **Deterministic distribution** based on `hotelId` shard key. |
| 3 | **Consistent Hashing** | `com.hotel.system.components.router` | MD5 hash ring with 100 virtual nodes per server. `O(log N)` lookup. | **33.40% remapping on node addition** vs **67.40%** in simple modulo. |
| 4 | **Key-Value Store** | `com.hotel.system.service.RoomService` | Redis 7.0 cache for room availability. Automatic invalidation on booking mutations. | **99.69% Hit Ratio**, **4.6 ms average lookup latency**. |
| 5 | **Unique ID Generator** | `com.hotel.system.components.idgenerator` | 64-bit Twitter Snowflake algorithm (timestamp, datacenter, machine, sequence). | **2,194,138 IDs/second** across 10 threads with **ZERO collisions**. |
| 6 | **URL Shortener** | `com.hotel.system.components.urlshortener` | Base62 encoding (`[a-zA-Z0-9]`) with dual Redis/Mongo storage. Redirects at `/r/{shortCode}`. | **< 1 ms redirection latency**, open-redirect security guard. |
| 7 | **Web Crawler** | `com.hotel.system.components.crawler` | Controlled fixture-driven JSoup parser for competitor room rates, amenities, and reviews. | **34.48 pages/second**, 100% schema extraction conformance. |
| 8 | **Autocomplete Engine** | `com.hotel.system.components.autocomplete` | Redis ZSet lexicographical range queries (`rangeByLex`) with in-memory Trie fallback. | **Sub-5 millisecond query latency**, debounced at 200 ms in React UI. |

---

## 5. Verification & Automated Testing

Run the complete automated test suite (Unit, Integration, Concurrency, and System Design components):
```bash
cd backend
mvn test -o
```
**Results:** `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` (100% Passing).

### Verification of Primary Invariant (Double-Booking Guard)
```bash
python3 scripts/run_failure_experiments.py
```
- 50 simultaneous booking requests targeting the exact same room and date interval.
- **Strictly 1 confirmed reservation succeeds**; **49 requests rejected** with `HTTP 409 Conflict`.
- Database invariant verified in MongoDB.

---

## 6. Performance Benchmarks

Run repeatable benchmarks across Low (10 VUs), Medium (50 VUs), and High (100 VUs) concurrency workloads:
```bash
python3 scripts/run_benchmarks.py
```
Outputs:
- Raw JSON: `docs/benchmarks/benchmark_results.json`
- Raw CSV: `docs/benchmarks/benchmark_results.csv`
- Matplotlib Graphs: `docs/benchmarks/*.png`

### Summary of Empirical Observations:
- **Cached Rooms API:** **5,970.69 QPS** at 15.5 ms latency under 100 concurrent VUs.
- **Autocomplete API:** **5,318.70 QPS** at 18.0 ms latency under 100 concurrent VUs.
- **Hotel Catalog API:** **1,720.13 QPS** at 52.9 ms latency under 100 concurrent VUs.

---

## 7. Project Documentation Index

- [`docs/architecture.md`](docs/architecture.md) — Architectural patterns, data flow, and scaling boundaries.
- [`docs/component-mapping.md`](docs/component-mapping.md) — Comprehensive matrix covering all 8 system-design components.
- [`docs/database-design.md`](docs/database-design.md) — Schemas, indexes, shard key selection, and cross-shard queries.
- [`docs/performance-evaluation.md`](docs/performance-evaluation.md) — Benchmark methodology, workload definitions, and results.
- [`docs/failure-analysis.md`](docs/failure-analysis.md) — Rate limiter overload, concurrency stress, and resilience analysis.
- [`docs/demo-guide.md`](docs/demo-guide.md) — Viva examination demonstration guide.
- [`report/report.tex`](report/report.tex) — Complete compile-ready LaTeX academic technical report.
# System_Design
