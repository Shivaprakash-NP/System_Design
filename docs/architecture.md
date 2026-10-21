# Scalable Hotel Management System - Architecture & System Design

**Course:** UCS3513 System Design Laboratory  
**Institution:** Sri Sivasubramaniya Nadar (SSN) College of Engineering, Chennai  
**Regulations:** 2024 | III Year CSE | Academic Year 2026–27  

---

## 1. System Architecture Overview

The system is designed as a **modular, scalable micro-monolith** optimized for low-latency operational throughput, high availability, and strong reservation consistency. It integrates a React/TypeScript/Vite single-page application (SPA), a Spring Boot 4.x/3.x backend running on Java 21, a primary MongoDB 6.0 persistence tier, and a Redis 7.0 in-memory acceleration and caching tier.

```mermaid
flowchart TD
    subgraph Client_Layer ["Client Presentation Layer (React 19 + Vite)"]
        SPA["Single Page Application (Port 5173)\nReact 19, TypeScript, Inter UI"]
        UI_Guest["Guest Portal\nSearch, Autocomplete, Booking, Invoices"]
        UI_Staff["Staff Portals\nFront Desk, Housekeeping, Diagnostics"]
    end

    subgraph Gateway_Layer ["API Entry & Traffic Governance Layer"]
        RateLimiter["Token Bucket Rate Limiter\n(100 req/min burst, Redis Lua Atomic Script)"]
        SecurityFilter["Spring Security + JWT Filter\n(Role-based Auth: ADMIN, RECEPTIONIST, HOUSEKEEPING, GUEST)"]
    end

    subgraph Service_Layer ["Core Business & Domain Services"]
        AuthSvc["AuthService\n(BCrypt, JWT Provider)"]
        HotelSvc["HotelService & RoomService\n(Catalog, Availability Invariant)"]
        BookingSvc["BookingService\n(Distributed Lock, Idempotency)"]
        BillingSvc["BillingService\n(12% GST, Itemized Invoice, Short URL)"]
        HousekeepingSvc["HousekeepingService\n(DIRTY, CLEANING, CLEAN, INSPECTED)"]
        NotificationSvc["NotificationService\n(In-app, Event Delivery)"]
    end

    subgraph System_Design_Tier ["System Design Infrastructure Components"]
        HashRing["Consistent Hash Ring\n(100 Virtual Nodes, MD5 Routing)"]
        SnowflakeGen["Snowflake 64-bit ID Generator\n(Time, Datacenter, Machine, Sequence)"]
        UrlShortener["Base62 URL Shortener\n(Redis Cache + Mongo)"]
        Crawler["Fixture Hotel Web Crawler\n(JSoup Multi-Page Intelligence)"]
        AutocompleteEngine["Autocomplete Engine\n(Redis ZSet Lexicographical + Trie)"]
        ShardRouter["ShardingService Router\n(Application-level Partitioning)"]
    end

    subgraph Data_Storage_Layer ["Data & Caching Storage Layer"]
        RedisCluster["Redis 7.0 Key-Value Store (Port 6380)\n- Room Availability Cache\n- Distributed Room Locks\n- Rate Limit Tokens\n- Autocomplete Lexicon\n- Short URL Cache"]
        MongoDB["MongoDB 6.0 Persistence (Port 27017)\n- users & roles\n- hotels & rooms\n- bookings (Snowflake ID)\n- invoices & billing\n- notifications\n- short_urls\n- competitor_rates\n(Logical Shards: shard_0, shard_1, shard_2)"]
    end

    %% Client Interactions
    SPA --> Gateway_Layer
    RateLimiter --> SecurityFilter
    SecurityFilter --> Service_Layer

    %% Service to System Design Component Connections
    BookingSvc --> SnowflakeGen
    BookingSvc --> RedisCluster
    BillingSvc --> UrlShortener
    HotelSvc --> AutocompleteEngine
    HotelSvc --> RedisCluster
    ShardRouter --> HashRing

    %% Service to Storage Connections
    Service_Layer --> ShardRouter
    ShardRouter --> MongoDB
    Service_Layer --> RedisCluster
```

---

## 2. Layer & Module Descriptions

### 2.1 Presentation Layer (Frontend)
- **Framework:** React 19, JavaScript/TypeScript, Vite build engine.
- **Theme:** Polished luxury dashboard aesthetic utilizing deep navy (`#0f172a`), teal accents (`#0d9488`), and subtle warm-gold highlights (`#d97706`).
- **Core Views:**
  1. `RoomSearch.jsx`: Autocomplete search, date range pickers `[checkIn, checkOut)`, real-time room cards with amenities, and modal booking flow.
  2. `GuestDashboard.jsx`: Guest booking history, active stay details, cancellation, and receipt view.
  3. `ReceptionDashboard.jsx`: Front desk check-in workflow, checkout processing, and invoice generation.
  4. `HousekeepingDashboard.jsx`: Room turnover Kanban grid, room cleaning state transitions (`DIRTY` $\to$ `CLEANING` $\to$ `CLEAN` $\to$ `INSPECTED`), and staff assignment.
  5. `InvoiceView.jsx`: Itemized receipt view displaying base accommodation charges, 12% GST tax breakdown, service fees, payment badges, and Base62 short links.
  6. `SystemDesignDashboard.jsx`: Real-time system design telemetry covering all 8 components, live remapping metrics, and collision benchmarks.

### 2.2 Security & Authentication Module
- **Algorithm:** BCrypt password hashing ($2^{10}$ rounds).
- **Token Format:** Signed JSON Web Tokens (JJWT 0.12.5) with HMAC-SHA512.
- **Role Hierarchy:**
  - `GUEST`: Room search, reservations, personal booking history, personal invoices, in-app notifications.
  - `RECEPTIONIST`: Guest check-in, checkout, bill generation, operational stay management.
  - `HOUSEKEEPING`: Room cleaning state updates, staff assignments, readiness verification.
  - `ADMIN`: Comprehensive access to all staff modules, system diagnostics, and hotel catalog administration.

### 2.3 Core Business Services
- **`BookingService`**: Manages reservation lifecycle. Enforces half-open interval validation `[checkInDate, checkOutDate)`. Coordinates with Redis distributed mutex to serialize concurrent booking attempts on the same room. Supports idempotency keys to suppress duplicate network submissions.
- **`BillingService`**: Generates itemized invoices upon checkout. Applies standard hospitality tax rules (12% GST), facility charges, records simulated payments, and publishes shortened links.
- **`HousekeepingService`**: Manages room availability transitions. Dirty or cleaning rooms are automatically blocked from customer-facing availability until certified clean or inspected.
- **`NotificationService`**: Delivers asynchronous, persistent in-app notifications for confirmation, check-in, checkout, and billing events.

---

## 3. Data Flow Workflows

### 3.1 Room Search & Autocomplete
1. Guest enters prefix (e.g. `"taj"`) into the search bar.
2. Keystroke is debounced (200 ms) and dispatched to `GET /api/v1/search/autocomplete?q=taj`.
3. Redis sorted set executes `ZRANGEBYLEX hotel:autocomplete [taj [taj\uffff` in $< 5$ ms.
4. Suggestions render instantaneously.
5. User selects dates and queries available rooms: `GET /api/v1/rooms/available-dates`.
6. `RoomService` checks Redis key `rooms:available:{hotelId}`. On cache hit ($99.69\%$ rate), rooms are returned from memory in $\approx 4.6$ ms. On cache miss, MongoDB is queried, and Redis is populated with a 10-minute TTL.

### 3.2 Concurrent Reservation & Double-Booking Guard
1. Client submits `POST /api/v1/bookings`.
2. Rate limiter evaluates token bucket. If tokens available, request proceeds; otherwise returns `HTTP 429`.
3. `BookingService` verifies date range validity ($checkOut > checkIn$).
4. System attempts to acquire Redis distributed lock `lock:room:{roomId}` with an 8-second lease.
5. **Critical Invariant Check:** Under lock, MongoDB is queried for any active reservation on `roomId` where $b.\text{checkIn} < \text{newCheckOut} \land b.\text{checkOut} > \text{newCheckIn}$.
6. If an overlap is detected, lock is released and `HTTP 409 Conflict` is returned.
7. If available, Snowflake generator produces a 64-bit unique ID, booking is saved to MongoDB, Redis room availability cache is evicted, an in-app notification is delivered, and lock is released.
8. Client receives `HTTP 201 Created`.

---

## 4. Scaling Boundaries & Consistency Guarantees

| Metric | Architecture Boundary | Operational Guarantee |
|---|---|---|
| **Booking Consistency** | Strictly Serialized via Redis Mutex | **Linearizable** per room. Zero overlapping confirmed bookings permitted under concurrent load. |
| **Availability Search** | Redis Key-Value Cache | **Eventually Consistent** (TTL 10 min, active invalidation on booking write). Authoritative check occurs before confirmation. |
| **ID Generation Capacity** | Snowflake 64-bit Generator | **4,096 unique IDs per millisecond per worker node** ($\approx 4.09$M IDs/sec). Clock rollback defensively handled. |
| **Horizontal Partitioning** | Consistent Hash Ring (100 V-nodes) | **Logarithmic $O(\log N)$ routing**. Measured remapping upon node addition is $33.4\%$ (matching theoretical $1/N$). |
