# Step-by-Step Viva & College Evaluation Demonstration Guide

**Project:** Scalable Hotel Management System  
**Course:** UCS3513 System Design Laboratory, III Year CSE  
**Institution:** Sri Sivasubramaniya Nadar (SSN) College of Engineering, Chennai  
**Academic Year:** 2026–27 | Regulations 2024  

---

## 1. System Startup Checklist

### Option A: One-Command Docker Compose (Full Stack)
```bash
# From workspace root:
docker compose up -d --build
```
- MongoDB accessible on port `27017`
- Redis accessible on port `6380` (mapped from 6379)
- Spring Boot Backend accessible on port `8080`
- React Frontend accessible on port `5173`

### Option B: Local Development Startup
```bash
# 1. Start Infrastructure Containers (Redis & Mongo):
docker compose up -d redis mongodb

# 2. Build & Run Backend:
cd backend
mvn clean package -DskipTests
java -jar target/system-0.0.1-SNAPSHOT.jar

# 3. Start Frontend (in separate terminal):
cd ../frontend
npm install
npm run build
npx vite preview --port 5173 --host
```

Open Browser: **`http://localhost:5173`**

---

## 2. Seed Demo Accounts Matrix

Use the **1-Click Quick Fill buttons** on the Login page (`http://localhost:5173/login`):

| Role | Email | Password | Access Privileges |
|---|---|---|---|
| **Guest** | `guest@hotel.demo` | `Guest@12345` | Hotel Search, Autocomplete, Book Room, My Bookings, In-app Notifications, Invoices |
| **Receptionist** | `reception@hotel.demo` | `Reception@12345` | Front Desk Portal, Guest Check-In, Process Check-Out, Generate Itemized Bills |
| **Housekeeping** | `housekeeping@hotel.demo` | `Housekeep@12345` | Housekeeping Kanban Dashboard, Status Transitions (DIRTY $\to$ CLEANING $\to$ CLEAN $\to$ INSPECTED), Staff Allocation |
| **Administrator** | `admin@hotel.demo` | `Admin@12345` | Unrestricted access across all operational, staff, and system-design benchmark portals |

---

## 3. End-to-End Evaluation Workflow Sequence

### Step 1: User Authentication & Role-Based Navigation
1. Navigate to `http://localhost:5173/login`.
2. Click the **"Guest Demo"** quick-fill button. Click **"Sign In"**.
3. Verify that the user navbar displays the teal **GUEST** badge and the user's name `John Guest`.

### Step 2: Hotel Search & Redis-Backed Autocomplete
1. On the **Search & Book** page, click into the search input.
2. Type `"taj"`. Observe that suggestions `"Taj Coromandel"` and `"Taj Exotica"` appear in $< 10$ ms via Redis ZSet lexicographical search.
3. Select `"Taj Coromandel"`. The hotel list filters immediately.
4. Select city filter `"Chennai"` or `"Mumbai"`. Observe instant catalog filtering.

### Step 3: Date-Based Room Availability & Booking Creation
1. Select hotel **Grand Palace Chennai**.
2. Pick Check-In Date and Check-Out Date (e.g., 3-night stay).
3. Observe available room cards showing Room Number, Room Type (e.g. SUITE), Price/Night, Amenities tags, and Clean badge.
4. Click **"Reserve Room"**.
5. The stay summary modal displays room charges ($3 \times \$145.00$), 12% GST tax estimate, and $15 service charge.
6. Click **"Confirm Booking"**.
7. Observe instantaneous confirmation with time-sortable 64-bit Snowflake ID (e.g. `#612984128938129`).
8. Notice the **Notification Bell** in the top navbar increments with an unread badge: *"Reservation Confirmed"*.

### Step 4: Verification of Concurrency Double-Booking Protection
1. Open a second browser tab or run the terminal concurrency script:
   ```bash
   python3 scripts/run_failure_experiments.py
   ```
2. Observe that 50 concurrent booking attempts for the same room and overlapping dates produce **strictly 1 successful reservation** and **49 rejected conflicts** (`HTTP 409`).
3. Attempting to book the same room for the same dates in the UI displays:
   `"⚠️ Room is already booked for these selected dates."`

### Step 5: Guest Booking History Portal
1. Click **"My Bookings"** in the navbar (`/my-bookings`).
2. Verify that the newly booked stay appears with status badge `CONFIRMED`.
3. Verify options to cancel or view stay details.

### Step 6: Front Desk Receptionist Workflow (Check-In & Check-Out)
1. Click **Logout** and log in with the **Receptionist** demo account (`reception@hotel.demo`).
2. Click **"Front Desk"** in the top navigation.
3. Locate the guest's confirmed reservation.
4. Click **"Check-In Guest"**. Observe status updates to `CHECKED_IN`.
5. When the guest departs, click **"Process Check-Out & Bill"**.
6. The system sets the reservation to `CHECKED_OUT`, marks the room status as `DIRTY`, invokes `BillingService` to calculate 12% GST, and generates an itemized invoice with a Base62 short URL.

### Step 7: Itemized Billing & Base62 URL Shortener
1. Click **"Receipt 📄"** to open the generated invoice (`/invoice/:invoiceId`).
2. Review the itemized charges:
   - Accommodation charges
   - Government Hotel Tax (12% GST)
   - Facility & Service Fee
   - Total Amount Paid with `PAID` badge
3. Observe the shortened notification link: `http://localhost:8080/r/{shortCode}`.
4. Paste the short link in a new browser tab; observe that it resolves instantaneously (HTTP 302 Found) to the full invoice view!

### Step 8: Housekeeping Operations Dashboard
1. Log in with the **Housekeeping** demo account (`housekeeping@hotel.demo`).
2. Click **"Housekeeping"** in the top navbar (`/housekeeping`).
3. Observe KPI summary cards: Clean, Dirty, Cleaning in progress, Inspected.
4. Find the room that was just checked out; observe its status is flagged `DIRTY` and its booking availability is marked `Blocked`.
5. Click **"Start Cleaning"** $\to$ status transitions to `CLEANING`.
6. Assign staff name (e.g. `"Staff Ramesh"`). Click **"Mark Clean"** $\to$ status updates to `CLEAN` and availability unlocks (`Ready`).
7. Click **"Certify Inspected"** $\to$ status certifies as `INSPECTED`.

### Step 9: System Design & Live Benchmarking Dashboard
1. Log in as **Admin** or click **"⚡ System Design & Benchmarks"** in the navbar.
2. Review the live telemetry for all 8 components:
   - **Rate Limiter:** Allowed requests, rejected requests (HTTP 429), reset button.
   - **Sharding:** Total partitioned documents, per-shard breakdown (`shard_0`, `shard_1`), imbalance factor, "+ Add Logical Shard" test.
   - **Consistent Hashing:** Virtual nodes count (100 per node), "Run Node Addition Remapping Test" button (measures 33.4% remapped vs 67.4% modulo).
   - **Key-Value Cache:** Redis cache hit ratio ($99.69\%$), lookup latency ($4.6$ ms).
   - **Snowflake ID Generator:** Click **"Execute Collision Benchmark"**; executes 25,000 concurrent ID generations across 10 threads and reports **ZERO duplicate collisions** at $> 2$ Million IDs/sec.
   - **Base62 URL Shortener:** Redirection counter, latency, open-redirect protection.
   - **Web Crawler:** Click **"Run Competitor Crawler Cycle"**; parses competitor HTML fixtures, extracts rates/amenities, and displays throughput ($34.48$ pages/sec).
   - **Autocomplete:** Prefix query latency and Trie fallback readiness.

### Step 10: Automated JUnit & Concurrency Test Verification
In the terminal, run:
```bash
cd backend
mvn test -o
```
Expected Output:
```
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
