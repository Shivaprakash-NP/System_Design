package com.hotel.system.security;

import com.hotel.system.components.ratelimiter.TokenBucketRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimiterFilter extends OncePerRequestFilter {

    private final TokenBucketRateLimiter rateLimiter;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String path = request.getRequestURI();

        // Skip static assets, error pages, and internal actuator endpoints
        if (path.startsWith("/actuator") || path.startsWith("/error") || path.startsWith("/favicon.ico")) {
            return true;
        }

        // Skip dashboard read-only telemetry pollers so background 10s auto-refresh doesn't deplete tokens
        if (path.startsWith("/api/v1/system-design")) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 1. Resolve client identifier
        String clientIdentifier = resolveClientIdentifier(request);

        // 2. Resolve descriptive architectural action
        String action = resolveAction(path, method);

        // 3. Consume token and record latency & trace
        TokenBucketRateLimiter.TokenConsumptionResult result = rateLimiter.consumeAndRecord(
                clientIdentifier,
                method,
                path,
                action,
                rateLimiter.getDefaultCapacity(),
                rateLimiter.getDefaultRefillRate()
        );

        // 4. Attach rate limiter response headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
        response.setHeader("X-RateLimit-Latency-Ms", String.format("%.3f", result.latencyMs()));

        // 5. Handle rate limit exceeded (HTTP 429)
        if (!result.allowed()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", "5");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Rate limit exceeded. Bucket tokens depleted.\",\"retryAfter\":5,\"tokensRemaining\":0,\"latencyMs\":"
                            + String.format("%.3f", result.latencyMs()) + "}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIdentifier(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isEmpty()) ? remoteAddr : "client_anon";
    }

    private String resolveAction(String path, String method) {
        if (path.startsWith("/api/v1/auth/login")) return "USER_LOGIN";
        if (path.startsWith("/api/v1/auth/register")) return "USER_REGISTER";
        if (path.startsWith("/api/v1/auth/me")) return "AUTH_VERIFY";
        if (path.startsWith("/api/v1/bookings") && "POST".equalsIgnoreCase(method)) return "BOOKING_CREATE";
        if (path.startsWith("/api/v1/bookings") && "GET".equalsIgnoreCase(method)) return "BOOKINGS_QUERY";
        if (path.contains("/check-in")) return "FRONT_DESK_CHECKIN";
        if (path.startsWith("/api/v1/rooms")) return "ROOM_AVAILABILITY";
        if (path.startsWith("/api/v1/hotels")) return "HOTEL_DIRECTORY";
        if (path.startsWith("/api/v1/search")) return "AUTOCOMPLETE_SEARCH";
        if (path.startsWith("/api/v1/billing") || path.startsWith("/api/v1/invoices")) return "INVOICE_BILLING";
        if (path.startsWith("/api/v1/notifications")) return "NOTIFICATIONS_FETCH";
        if (path.startsWith("/api/v1/housekeeping")) return "HOUSEKEEPING_DISPATCH";
        if (path.startsWith("/short-url") || path.startsWith("/r/")) return "URL_REDIRECT";

        return method + " " + path;
    }
}
