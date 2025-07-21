package com.example.productapi.filter;

import com.example.productapi.config.RateLimitConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    private final RateLimitConfig rateLimitConfig;
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitingFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    // Only apply to /product endpoints
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return !request.getRequestURI().startsWith("/products");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Bucket bucket = bucketCache.computeIfAbsent(ip, k -> newBucket());
        if (bucket.tryConsume(1)){
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests - wait and try again.");
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                rateLimitConfig.getRequests(),
                Refill.greedy(rateLimitConfig.getRequests(), Duration.ofMinutes(rateLimitConfig.getDurationMinutes()))
        );

        return Bucket4j.builder().addLimit(limit).build();
    }
}
