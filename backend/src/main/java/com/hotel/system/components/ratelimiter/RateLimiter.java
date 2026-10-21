package com.hotel.system.components.ratelimiter;

public interface RateLimiter {
    boolean isAllowed(String key);
    boolean isAllowed(String key, int capacity, int refillRate);
    long getAllowedRequests();
    long getRejectedRequests();
    double getRejectionRate();
    void resetMetrics();
}
