package com.digitalwill.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * HTTP filter enforcing rate limits on sensitive unauthenticated endpoints (Workstream 7).
 * Rejects bursts exceeding thresholds with HTTP 429 and Retry-After headers.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    public RateLimitingFilter(RateLimitService rateLimitService, RateLimitProperties properties) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        String clientIp = resolveClientIp(request);
        RateLimitService.RateLimitDecision decision = null;

        if ("POST".equalsIgnoreCase(method) && "/api/auth/login".equals(path)) {
            decision = rateLimitService.checkLimit("auth_login:" + clientIp,
                    properties.getAuthLimit(), properties.getWindowSeconds());
        } else if ("POST".equalsIgnoreCase(method) && "/api/auth/register".equals(path)) {
            decision = rateLimitService.checkLimit("auth_register:" + clientIp,
                    properties.getAuthLimit(), properties.getWindowSeconds());
        } else if ("POST".equalsIgnoreCase(method) && "/api/verification/confirm".equals(path)) {
            decision = rateLimitService.checkLimit("verification_confirm:" + clientIp,
                    properties.getVerificationLimit(), properties.getWindowSeconds());
        } else if (path != null && path.startsWith("/api/disclosure/")) {
            decision = rateLimitService.checkLimit("disclosure:" + clientIp,
                    properties.getDisclosureLimit(), properties.getWindowSeconds());
        }

        if (decision != null && !decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String json = String.format(
                    "{\"timestamp\":\"%s\",\"status\":429,\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Please try again later.\",\"retryAfter\":%d}",
                    Instant.now(), decision.retryAfterSeconds()
            );
            response.getWriter().write(json);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] parts = xForwardedFor.split(",");
            return parts[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
