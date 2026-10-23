package com.hotel.system.components.crawler;

import com.hotel.system.model.ImportedCompetitorRate;
import com.hotel.system.repository.ImportedCompetitorRateRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ExternalHotelCrawler {

    private static final Logger logger = LoggerFactory.getLogger(ExternalHotelCrawler.class);

    private final ImportedCompetitorRateRepository competitorRateRepository;

    // Metrics for background competitor cycle
    private final AtomicInteger totalPagesCrawled = new AtomicInteger(0);
    private final AtomicInteger successfulPages = new AtomicInteger(0);
    private final AtomicInteger failedPages = new AtomicInteger(0);
    private final AtomicLong totalCrawlDurationMs = new AtomicLong(0);

    public ExternalHotelCrawler(ImportedCompetitorRateRepository competitorRateRepository) {
        this.competitorRateRepository = competitorRateRepository;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawledPageDetail {
        private String url;
        private String title;
        private int httpStatus;
        private int depth;
        private long responseTimeMs;
        private int contentSizeBytes;
        private int discoveredLinksCount;
        private List<String> discoveredLinks;
        private String metaDescription;
        private String status; // SUCCESS, FAILED, BLOCKED_BY_ROBOTS
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawlReport {
        private String seedUrl;
        private String status; // COMPLETED, PARTIAL, FAILED
        private int totalPagesCrawled;
        private int discoveredLinksCount;
        private int queueRemaining;
        private int maxDepthReached;
        private long durationMs;
        private String robotsTxtPolicy;
        private List<CrawledPageDetail> crawledPages;
        private String errorMessage;
    }

    private static class CrawlTask {
        final String url;
        final int depth;

        CrawlTask(String url, int depth) {
            this.url = url;
            this.depth = depth;
        }
    }

    /**
     * Bounded, polite live web crawl starting from a user-supplied seed URL.
     * Enforces SSRF prevention, robots.txt compliance, timeouts, and bounded limits.
     */
    public CrawlReport crawlUrl(String seedUrl, int requestedMaxPages, int requestedMaxDepth) {
        long startTime = System.currentTimeMillis();
        int maxPages = Math.min(Math.max(1, requestedMaxPages), 15); // clamp 1-15
        int maxDepth = Math.min(Math.max(1, requestedMaxDepth), 3);   // clamp 1-3

        List<CrawledPageDetail> pageDetails = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<CrawlTask> queue = new ArrayDeque<>();
        Set<String> allDiscoveredLinks = new LinkedHashSet<>();
        int maxDepthReached = 0;

        // 1. Safe URL Validation & SSRF Prevention
        try {
            validateUrlSafe(seedUrl);
        } catch (SecurityException | IllegalArgumentException e) {
            return CrawlReport.builder()
                    .seedUrl(seedUrl)
                    .status("FAILED")
                    .totalPagesCrawled(0)
                    .discoveredLinksCount(0)
                    .queueRemaining(0)
                    .maxDepthReached(0)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .robotsTxtPolicy("NOT_EVALUATED")
                    .crawledPages(List.of())
                    .errorMessage(e.getMessage())
                    .build();
        }

        // 2. Robots.txt evaluation
        URI seedUri = URI.create(seedUrl);
        String host = seedUri.getHost();
        String protocol = seedUri.getScheme();
        int port = seedUri.getPort();
        Set<String> disallowedPrefixes = fetchDisallowedPaths(protocol, host, port);
        String robotsStatus = disallowedPrefixes.isEmpty() 
                ? "No restrictive rules found in robots.txt (Allowed)" 
                : "Parsed robots.txt (" + disallowedPrefixes.size() + " path prefix rules enforced)";

        // 3. Initialize BFS Frontier
        String canonicalSeed = canonicalizeUrl(seedUrl);
        queue.add(new CrawlTask(canonicalSeed, 0));
        visited.add(canonicalSeed);

        // 4. Execute Bounded BFS Crawl Loop
        while (!queue.isEmpty() && pageDetails.size() < maxPages) {
            CrawlTask current = queue.poll();
            if (current.depth > maxDepthReached) {
                maxDepthReached = current.depth;
            }

            // Check robots.txt
            URI currentUri = URI.create(current.url);
            String path = currentUri.getPath() != null ? currentUri.getPath() : "/";
            boolean isDisallowed = false;
            for (String prefix : disallowedPrefixes) {
                if (path.startsWith(prefix)) {
                    isDisallowed = true;
                    break;
                }
            }

            if (isDisallowed) {
                pageDetails.add(CrawledPageDetail.builder()
                        .url(current.url)
                        .title("N/A - Blocked by Robots.txt")
                        .httpStatus(403)
                        .depth(current.depth)
                        .responseTimeMs(0)
                        .contentSizeBytes(0)
                        .discoveredLinksCount(0)
                        .discoveredLinks(List.of())
                        .status("BLOCKED_BY_ROBOTS")
                        .error("Excluded by robots.txt directive for path: " + path)
                        .build());
                continue;
            }

            // Fetch page with polite timeouts
            long pageFetchStart = System.currentTimeMillis();
            try {
                // Verify host is still safe before each fetch
                validateUrlSafe(current.url);

                Connection connection = Jsoup.connect(current.url)
                        .userAgent("BookingCom-Enterprise-Crawler/1.0 (+http://localhost:5173/system-design)")
                        .timeout(4000)
                        .maxBodySize(512 * 1024) // 512 KB
                        .followRedirects(true)
                        .ignoreHttpErrors(true);

                Connection.Response response = connection.execute();
                long latency = System.currentTimeMillis() - pageFetchStart;

                int statusCode = response.statusCode();
                byte[] bodyBytes = response.bodyAsBytes();
                int contentSize = bodyBytes != null ? bodyBytes.length : 0;

                Document doc = response.parse();
                String title = doc.title();
                if (title == null || title.trim().isEmpty()) {
                    title = "Untitled Page (" + currentUri.getHost() + ")";
                }

                Element metaDesc = doc.selectFirst("meta[name=description]");
                String description = metaDesc != null ? metaDesc.attr("content") : "";

                // Check meta robots
                Element metaRobots = doc.selectFirst("meta[name=robots]");
                boolean nofollow = metaRobots != null && metaRobots.attr("content").toLowerCase().contains("nofollow");

                List<String> pageDiscoveredLinks = new ArrayList<>();
                if (!nofollow) {
                    Elements links = doc.select("a[href]");
                    for (Element link : links) {
                        String absUrl = link.attr("abs:href");
                        if (absUrl != null && (absUrl.startsWith("http://") || absUrl.startsWith("https://"))) {
                            String clean = canonicalizeUrl(absUrl);
                            allDiscoveredLinks.add(clean);
                            if (pageDiscoveredLinks.size() < 12) {
                                pageDiscoveredLinks.add(clean);
                            }

                            // Add to queue if within depth and not yet visited
                            if (current.depth + 1 <= maxDepth && visited.add(clean)) {
                                queue.add(new CrawlTask(clean, current.depth + 1));
                            }
                        }
                    }
                }

                pageDetails.add(CrawledPageDetail.builder()
                        .url(current.url)
                        .title(title)
                        .httpStatus(statusCode)
                        .depth(current.depth)
                        .responseTimeMs(latency)
                        .contentSizeBytes(contentSize)
                        .discoveredLinksCount(pageDiscoveredLinks.size())
                        .discoveredLinks(pageDiscoveredLinks)
                        .metaDescription(description)
                        .status("SUCCESS")
                        .build());

                // Polite crawler delay between requests
                Thread.sleep(120);

            } catch (SocketTimeoutException ste) {
                long latency = System.currentTimeMillis() - pageFetchStart;
                pageDetails.add(CrawledPageDetail.builder()
                        .url(current.url)
                        .title("Connection Timed Out")
                        .httpStatus(504)
                        .depth(current.depth)
                        .responseTimeMs(latency)
                        .status("FAILED")
                        .error("Read/connect timed out after 4000ms: " + ste.getMessage())
                        .build());
            } catch (UnknownHostException uhe) {
                pageDetails.add(CrawledPageDetail.builder()
                        .url(current.url)
                        .title("Host Resolution Failed")
                        .httpStatus(502)
                        .depth(current.depth)
                        .status("FAILED")
                        .error("Unknown host or network interface unreachable: " + uhe.getMessage())
                        .build());
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - pageFetchStart;
                pageDetails.add(CrawledPageDetail.builder()
                        .url(current.url)
                        .title("Crawl Error")
                        .httpStatus(500)
                        .depth(current.depth)
                        .responseTimeMs(latency)
                        .status("FAILED")
                        .error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                        .build());
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        String finalStatus = pageDetails.isEmpty() ? "FAILED" : (pageDetails.size() < maxPages ? "COMPLETED" : "BOUND_REACHED");

        return CrawlReport.builder()
                .seedUrl(seedUrl)
                .status(finalStatus)
                .totalPagesCrawled(pageDetails.size())
                .discoveredLinksCount(allDiscoveredLinks.size())
                .queueRemaining(queue.size())
                .maxDepthReached(maxDepthReached)
                .durationMs(totalDuration)
                .robotsTxtPolicy(robotsStatus)
                .crawledPages(pageDetails)
                .build();
    }

    /**
     * Strict SSRF validation against loopback, private networks, and internal hostnames.
     */
    private void validateUrlSafe(String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Seed URL cannot be empty.");
        }
        URI uri;
        try {
            uri = URI.create(urlStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed URL: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only HTTP and HTTPS protocols are supported for crawling.");
        }

        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("URL host cannot be empty.");
        }

        // Direct blacklist checks
        String lowerHost = host.toLowerCase();
        if (lowerHost.equals("localhost") || lowerHost.equals("0.0.0.0") || lowerHost.equals("127.0.0.1") ||
            lowerHost.endsWith(".local") || lowerHost.endsWith(".internal") || lowerHost.endsWith(".corp")) {
            throw new SecurityException("SSRF Protection: Access to localhost or internal network destination '" + host + "' is blocked.");
        }

        // Resolve DNS and test IP addresses
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() ||
                    addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                    throw new SecurityException("SSRF Protection: Target IP [" + addr.getHostAddress() + "] is a private/internal network address and is blocked.");
                }
            }
        } catch (UnknownHostException uhe) {
            // Let the crawler attempt or report DNS failure
            logger.debug("Host resolution check for {}: {}", host, uhe.getMessage());
        }
    }

    private Set<String> fetchDisallowedPaths(String protocol, String host, int port) {
        Set<String> disallowed = new HashSet<>();
        try {
            String robotsUrl = protocol + "://" + host + (port > 0 && port != 80 && port != 443 ? ":" + port : "") + "/robots.txt";
            Connection.Response resp = Jsoup.connect(robotsUrl)
                    .userAgent("BookingCom-Enterprise-Crawler/1.0")
                    .timeout(2000)
                    .ignoreHttpErrors(true)
                    .execute();

            if (resp.statusCode() == 200 && resp.body() != null) {
                String body = resp.body();
                boolean appliesToAll = false;
                for (String line : body.split("\n")) {
                    line = line.trim();
                    if (line.toLowerCase().startsWith("user-agent:")) {
                        String ua = line.substring(11).trim();
                        appliesToAll = ua.equals("*") || ua.toLowerCase().contains("bookingcom");
                    } else if (appliesToAll && line.toLowerCase().startsWith("disallow:")) {
                        String disPath = line.substring(9).trim();
                        if (!disPath.isEmpty()) {
                            disallowed.add(disPath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Robots.txt check skipped or absent for {}: {}", host, e.getMessage());
        }
        return disallowed;
    }

    private String canonicalizeUrl(String url) {
        int hashIdx = url.indexOf('#');
        if (hashIdx != -1) {
            url = url.substring(0, hashIdx);
        }
        if (url.endsWith("/") && url.length() > 8) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    // Existing methods preserved for competitor pricing intelligence
    public Map<String, Object> runCrawl() {
        long startTime = System.currentTimeMillis();
        List<SimulatedPage> pages = getSimulatedCompetitorPages();
        List<ImportedCompetitorRate> crawledRecords = new ArrayList<>();

        for (SimulatedPage page : pages) {
            totalPagesCrawled.incrementAndGet();
            try {
                Document doc = Jsoup.parse(page.htmlContent, page.sourceUrl);
                Elements roomElements = doc.select(".room-card");
                String hotelName = doc.selectFirst(".hotel-title") != null 
                        ? doc.selectFirst(".hotel-title").text() 
                        : "Simulated Competitor";

                double rating = 4.5;
                int reviews = 120;
                for (Element roomEl : roomElements) {
                    String roomType = roomEl.attr("data-type");
                    double price = Double.parseDouble(roomEl.attr("data-price"));
                    String currency = roomEl.attr("data-currency");

                    List<String> amenities = new ArrayList<>();
                    Elements amenityEls = roomEl.select(".amenity-tag");
                    for (Element a : amenityEls) {
                        amenities.add(a.text());
                    }

                    ImportedCompetitorRate rate = ImportedCompetitorRate.builder()
                            .competitorName(hotelName)
                            .sourceUrl(page.sourceUrl)
                            .roomType(roomType)
                            .observedPrice(price)
                            .currency(currency != null && !currency.isEmpty() ? currency : "USD")
                            .amenities(amenities)
                            .rating(rating)
                            .reviewCount(reviews)
                            .status("SUCCESS")
                            .crawledAt(LocalDateTime.now())
                            .build();

                    crawledRecords.add(rate);
                }
                successfulPages.incrementAndGet();
            } catch (Exception e) {
                failedPages.incrementAndGet();
            }
        }

        if (!crawledRecords.isEmpty()) {
            competitorRateRepository.saveAll(crawledRecords);
        }

        long duration = System.currentTimeMillis() - startTime;
        totalCrawlDurationMs.addAndGet(duration);

        return Map.of(
                "pagesCrawled", pages.size(),
                "recordsImported", crawledRecords.size(),
                "durationMs", duration,
                "status", "SUCCESS"
        );
    }

    public Map<String, Object> getCrawlerMetrics() {
        int total = totalPagesCrawled.get();
        int success = successfulPages.get();
        double successRate = total > 0 ? (success * 100.0 / total) : 100.0;
        double throughput = totalCrawlDurationMs.get() > 0 
                ? (total / (totalCrawlDurationMs.get() / 1000.0)) 
                : 0.0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalPagesCrawled", total);
        metrics.put("successfulPages", success);
        metrics.put("failedPages", failedPages.get());
        metrics.put("successRate", String.format("%.1f%%", successRate));
        metrics.put("averageThroughputPagesPerSec", String.format("%.2f", throughput));
        metrics.put("engine", "Jsoup DOM Parser with Robots.txt & SSRF Validation");
        return metrics;
    }

    public List<ImportedCompetitorRate> getAllCrawledRates() {
        return competitorRateRepository.findAll();
    }

    private static class SimulatedPage {
        final String sourceUrl;
        final String htmlContent;

        SimulatedPage(String sourceUrl, String htmlContent) {
            this.sourceUrl = sourceUrl;
            this.htmlContent = htmlContent;
        }
    }

    private List<SimulatedPage> getSimulatedCompetitorPages() {
        String html1 = """
            <!DOCTYPE html>
            <html>
            <head><title>Grand Palace Competitor Rates</title></head>
            <body>
                <h1 class="hotel-title">Grand Palace Competitor</h1>
                <div class="room-card" data-type="SINGLE" data-price="89.00" data-currency="USD">
                    <span class="amenity-tag">WiFi</span>
                </div>
                <div class="room-card" data-type="DOUBLE" data-price="139.00" data-currency="USD">
                    <span class="amenity-tag">Pool</span>
                </div>
            </body>
            </html>
            """;
        return List.of(new SimulatedPage("http://simulated-competitor.demo/rates", html1));
    }
}
