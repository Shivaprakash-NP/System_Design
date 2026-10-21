#!/usr/bin/env python3
"""
UCS3513 System Design Laboratory - Scalable Hotel Management System
Empirical Performance Benchmark Suite
Executes real load tests across Low (10 VUs), Medium (50 VUs), High (100 VUs) concurrency levels using 'hey',
measures latency, throughput, error rates, CPU/RAM, cache hit rates, sharding distribution,
ID generation collisions, and consistent hashing remapping.
Exports results to JSON, CSV, and generates matplotlib graphs.
"""

import subprocess
import json
import csv
import os
import re
import time
import urllib.request
import urllib.parse
import matplotlib.pyplot as plt

OUTPUT_DIR = "docs/benchmarks"
os.makedirs(OUTPUT_DIR, exist_ok=True)

HEY_PATH = os.path.expanduser("~/go/bin/hey")
BASE_URL = "http://localhost:8080"

def get_sys_metrics():
    """Reads system CPU and memory usage from Linux /proc."""
    try:
        with open("/proc/loadavg", "r") as f:
            load = f.read().split()[0]
        with open("/proc/meminfo", "r") as f:
            lines = f.readlines()
            total = int([l for l in lines if "MemTotal" in l][0].split()[1])
            avail = int([l for l in lines if "MemAvailable" in l][0].split()[1])
            mem_pct = round((1 - (avail / total)) * 100, 2)
        return float(load), mem_pct
    except Exception:
        return 0.0, 0.0

def run_hey(url, concurrency, requests, method="GET", body=None, headers=None):
    cmd = [HEY_PATH, "-n", str(requests), "-c", str(concurrency), "-m", method]
    if headers:
        for k, v in headers.items():
            cmd.extend(["-H", f"{k}: {v}"])
    if body:
        cmd.extend(["-d", body])
    cmd.append(url)

    res = subprocess.run(cmd, capture_output=True, text=True)
    out = res.stdout

    # Parse hey output
    # Requests/sec: 1234.56
    qps_match = re.search(r"Requests/sec:\s+([\d\.]+)", out)
    qps = float(qps_match.group(1)) if qps_match else 0.0

    # Average: 0.0123 secs
    avg_match = re.search(r"Average:\s+([\d\.]+)\s+secs", out)
    avg_ms = float(avg_match.group(1)) * 1000 if avg_match else 0.0

    # 95% in 0.0234 secs
    p95_match = re.search(r"95%\s+in\s+([\d\.]+)\s+secs", out)
    p95_ms = float(p95_match.group(1)) * 1000 if p95_match else 0.0

    # 99% in 0.0345 secs
    p99_match = re.search(r"99%\s+in\s+([\d\.]+)\s+secs", out)
    p99_ms = float(p99_match.group(1)) * 1000 if p99_match else 0.0

    # Status code distribution
    status_200 = re.search(r"\[200\]\s+(\d+)\s+responses", out)
    status_201 = re.search(r"\[201\]\s+(\d+)\s+responses", out)
    status_429 = re.search(r"\[429\]\s+(\d+)\s+responses", out)

    s200 = int(status_200.group(1)) if status_200 else 0
    s201 = int(status_201.group(1)) if status_201 else 0
    s429 = int(status_429.group(1)) if status_429 else 0
    success = s200 + s201
    failed = requests - success
    error_rate = (failed / requests) * 100 if requests > 0 else 0.0

    load, mem = get_sys_metrics()

    return {
        "concurrency": concurrency,
        "totalRequests": requests,
        "successfulRequests": success,
        "failedRequests": failed,
        "rateLimit429s": s429,
        "errorRatePct": round(error_rate, 2),
        "throughputQps": round(qps, 2),
        "avgLatencyMs": round(avg_ms, 2),
        "p95LatencyMs": round(p95_ms, 2),
        "p99LatencyMs": round(p99_ms, 2),
        "systemLoad": load,
        "memoryUsagePct": mem
    }

def fetch_json(endpoint):
    try:
        with urllib.request.urlopen(f"{BASE_URL}{endpoint}") as response:
            return json.loads(response.read().decode())
    except Exception as e:
        return {"error": str(e)}

def post_json(endpoint):
    try:
        req = urllib.request.Request(f"{BASE_URL}{endpoint}", data=b"", method="POST")
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read().decode())
    except Exception as e:
        return {"error": str(e)}

def main():
    print("=" * 70)
    print("STARTING UCS3513 SYSTEM DESIGN SYSTEM BENCHMARK SUITE")
    print("=" * 70)

    workload_levels = [
        {"name": "LOW", "concurrency": 10, "requests": 200},
        {"name": "MEDIUM", "concurrency": 50, "requests": 1000},
        {"name": "HIGH", "concurrency": 100, "requests": 2000}
    ]

    benchmark_data = {
        "metadata": {
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "academicContext": "UCS3513 System Design Laboratory, SSN College of Engineering",
            "environment": "Java 21, Spring Boot 4.x/3.x, MongoDB 6.0, Redis 7.0-alpine",
            "tool": "hey (HTTP load testing engine)"
        },
        "workloads": {},
        "systemDesignComponents": {}
    }

    # 1. Benchmark Hotel Catalog / Search across 3 concurrency levels
    print("\n--- 1. Evaluating Hotel Search API (/api/v1/hotels) ---")
    search_results = []
    for wl in workload_levels:
        print(f"Running Workload {wl['name']} (Concurrency: {wl['concurrency']}, Total: {wl['requests']})...")
        res = run_hey(f"{BASE_URL}/api/v1/hotels", wl["concurrency"], wl["requests"])
        res["workload"] = wl["name"]
        search_results.append(res)
        print(f"  -> QPS: {res['throughputQps']}, Avg Latency: {res['avgLatencyMs']} ms, p95: {res['p95LatencyMs']} ms, Errors: {res['errorRatePct']}%")
    benchmark_data["workloads"]["hotelSearch"] = search_results

    # 2. Benchmark Autocomplete (/api/v1/search/autocomplete?q=taj)
    print("\n--- 2. Evaluating Redis Autocomplete API (/api/v1/search/autocomplete) ---")
    autocomplete_results = []
    for wl in workload_levels:
        print(f"Running Workload {wl['name']} (Concurrency: {wl['concurrency']}, Total: {wl['requests']})...")
        res = run_hey(f"{BASE_URL}/api/v1/search/autocomplete?q=taj", wl["concurrency"], wl["requests"])
        res["workload"] = wl["name"]
        autocomplete_results.append(res)
        print(f"  -> QPS: {res['throughputQps']}, Avg Latency: {res['avgLatencyMs']} ms, p95: {res['p95LatencyMs']} ms")
    benchmark_data["workloads"]["autocomplete"] = autocomplete_results

    # 3. Benchmark Available Rooms (Key-Value Store Cache vs Uncached DB)
    print("\n--- 3. Evaluating Available Rooms (Redis Cached vs DB Lookup) ---")
    cached_results = []
    for wl in workload_levels:
        print(f"Running Cached Rooms Workload {wl['name']}...")
        res = run_hey(f"{BASE_URL}/api/v1/rooms/available/hotel_101", wl["concurrency"], wl["requests"])
        res["workload"] = wl["name"]
        cached_results.append(res)
        print(f"  -> QPS: {res['throughputQps']}, Avg Latency: {res['avgLatencyMs']} ms, p95: {res['p95LatencyMs']} ms")
    benchmark_data["workloads"]["cachedAvailableRooms"] = cached_results

    # 4. System Design Components Live Telemetry
    print("\n--- 4. Collecting Live System Design Component Measurements ---")
    
    # Rate Limiter Stats
    rl_stats = fetch_json("/api/v1/system-design/rate-limiter/stats")
    print("Rate Limiter:", json.dumps(rl_stats))
    benchmark_data["systemDesignComponents"]["rateLimiter"] = rl_stats

    # Sharding Stats
    sh_stats = fetch_json("/api/v1/system-design/sharding/stats")
    print("Sharding Stats:", json.dumps(sh_stats))
    benchmark_data["systemDesignComponents"]["sharding"] = sh_stats

    # Consistent Hashing Remapping Experiment vs Modulo
    print("Executing Consistent Hashing Remapping Experiment...")
    ch_remapping = post_json("/api/v1/system-design/consistent-hashing/test-remapping?newNode=shard_eval_node&sampleKeysCount=500")
    print("Consistent Hash Remapping:", json.dumps(ch_remapping))
    benchmark_data["systemDesignComponents"]["consistentHashing"] = ch_remapping

    # Cache Stats
    ca_stats = fetch_json("/api/v1/system-design/cache/stats")
    print("Cache Stats:", json.dumps(ca_stats))
    benchmark_data["systemDesignComponents"]["cache"] = ca_stats

    # Unique ID Generator Benchmark
    print("Executing Snowflake 64-bit ID Collision Benchmark (50,000 IDs across 10 threads)...")
    id_bench = post_json("/api/v1/system-design/id-generator/benchmark?totalIds=50000&threads=10")
    print("Snowflake ID Benchmark:", json.dumps(id_bench))
    benchmark_data["systemDesignComponents"]["idGenerator"] = id_bench

    # URL Shortener Stats
    us_stats = fetch_json("/api/v1/system-design/url-shortener/stats")
    print("URL Shortener Stats:", json.dumps(us_stats))
    benchmark_data["systemDesignComponents"]["urlShortener"] = us_stats

    # Web Crawler Execution
    print("Executing Web Crawler Cycle on Competitor Fixtures...")
    cr_bench = post_json("/api/v1/system-design/crawler/run")
    cr_stats = fetch_json("/api/v1/system-design/crawler/stats")
    benchmark_data["systemDesignComponents"]["crawler"] = {"execution": cr_bench, "stats": cr_stats}
    print("Crawler Stats:", json.dumps(cr_stats))

    # Autocomplete Telemetry
    ac_stats = fetch_json("/api/v1/system-design/autocomplete/stats")
    benchmark_data["systemDesignComponents"]["autocomplete"] = ac_stats

    # Save to JSON
    json_path = os.path.join(OUTPUT_DIR, "benchmark_results.json")
    with open(json_path, "w") as f:
        json.dump(benchmark_data, f, indent=2)
    print(f"\n[+] Raw Benchmark JSON saved to: {json_path}")

    # Save to CSV
    csv_path = os.path.join(OUTPUT_DIR, "benchmark_results.csv")
    with open(csv_path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["Workload", "Endpoint", "Concurrency (VUs)", "Total Requests", "Success", "Failed", "Error Rate (%)", "Throughput (QPS)", "Avg Latency (ms)", "p95 Latency (ms)", "p99 Latency (ms)", "System Load", "RAM (%)"])
        for ep_name, items in benchmark_data["workloads"].items():
            for it in items:
                writer.writerow([it["workload"], ep_name, it["concurrency"], it["totalRequests"], it["successfulRequests"], it["failedRequests"], it["errorRatePct"], it["throughputQps"], it["avgLatencyMs"], it["p95LatencyMs"], it["p99LatencyMs"], it["systemLoad"], it["memoryUsagePct"]])
    print(f"[+] Raw Benchmark CSV saved to: {csv_path}")

    # Generate Matplotlib Performance Graphs
    generate_charts(benchmark_data, OUTPUT_DIR)
    print(f"[+] Visual Performance Graphs saved to: {OUTPUT_DIR}/")

def generate_charts(data, output_dir):
    categories = ["LOW (10)", "MEDIUM (50)", "HIGH (100)"]
    
    # 1. Throughput Chart
    plt.figure(figsize=(9, 5))
    search_qps = [x["throughputQps"] for x in data["workloads"]["hotelSearch"]]
    auto_qps = [x["throughputQps"] for x in data["workloads"]["autocomplete"]]
    cached_qps = [x["throughputQps"] for x in data["workloads"]["cachedAvailableRooms"]]

    x = range(len(categories))
    width = 0.25

    plt.bar([i - width for i in x], search_qps, width=width, label="Hotel Search", color="#0f172a")
    plt.bar(x, auto_qps, width=width, label="Autocomplete (Redis)", color="#0d9488")
    plt.bar([i + width for i in x], cached_qps, width=width, label="Available Rooms (Cached)", color="#d97706")

    plt.xlabel("Workload Concurrency Level (VUs)")
    plt.ylabel("Throughput (Requests / Second)")
    plt.title("System Throughput Across Workload Levels (Low, Medium, High)")
    plt.xticks(x, categories)
    plt.legend()
    plt.grid(axis="y", linestyle="--", alpha=0.5)
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "throughput_comparison.png"), dpi=200)
    plt.close()

    # 2. Latency Chart (Avg & p95)
    plt.figure(figsize=(9, 5))
    search_avg = [x["avgLatencyMs"] for x in data["workloads"]["hotelSearch"]]
    search_p95 = [x["p95LatencyMs"] for x in data["workloads"]["hotelSearch"]]
    cached_avg = [x["avgLatencyMs"] for x in data["workloads"]["cachedAvailableRooms"]]
    cached_p95 = [x["p95LatencyMs"] for x in data["workloads"]["cachedAvailableRooms"]]

    plt.plot(categories, search_avg, marker="o", linewidth=2, color="#0f172a", label="Hotel Search (Avg)")
    plt.plot(categories, search_p95, marker="s", linestyle="--", linewidth=2, color="#334155", label="Hotel Search (p95)")
    plt.plot(categories, cached_avg, marker="o", linewidth=2, color="#0d9488", label="Cached Rooms (Avg)")
    plt.plot(categories, cached_p95, marker="s", linestyle="--", linewidth=2, color="#14b8a6", label="Cached Rooms (p95)")

    plt.xlabel("Workload Level")
    plt.ylabel("Response Time (Milliseconds)")
    plt.title("Response Time & p95 Tail Latency Comparison")
    plt.legend()
    plt.grid(True, linestyle="--", alpha=0.5)
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "latency_comparison.png"), dpi=200)
    plt.close()

    # 3. Consistent Hashing vs Modulo Remapping Chart
    if "consistentHashing" in data["systemDesignComponents"]:
        ch = data["systemDesignComponents"]["consistentHashing"]
        plt.figure(figsize=(7, 4.5))
        methods = ["Consistent Hashing\n(Virtual Nodes)", "Modulo Hashing\n(Simple)"]
        vals = [ch["consistentRemappedCount"], ch["moduloRemappedCount"]]
        colors = ["#0d9488", "#ef4444"]

        bars = plt.bar(methods, vals, color=colors, width=0.45)
        for bar in bars:
            height = bar.get_height()
            plt.text(bar.get_x() + bar.get_width()/2., height + 3,
                     f"{height} keys ({height/ch['sampleKeysCount']*100:.1f}%)",
                     ha="center", va="bottom", fontsize=10, fontweight="bold")

        plt.ylabel("Keys Remapped (out of 500)")
        plt.title("Cluster Resharding: Keys Remapped on Node Addition")
        plt.ylim(0, max(vals) * 1.2)
        plt.grid(axis="y", linestyle="--", alpha=0.5)
        plt.tight_layout()
        plt.savefig(os.path.join(output_dir, "consistent_hashing_remapping.png"), dpi=200)
        plt.close()

if __name__ == "__main__":
    main()
