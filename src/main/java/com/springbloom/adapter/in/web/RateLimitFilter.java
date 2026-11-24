package com.springbloom.adapter.in.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import com.springbloom.config.RateLimitProperties;
import com.springbloom.config.RateLimitProperties.Rule;
import com.springbloom.domain.model.GuardrailEvent.Stage;
import com.springbloom.domain.port.out.GuardrailEventRepository;
import com.springbloom.domain.service.guardrail.GuardrailMessages;
import com.springbloom.domain.service.ratelimit.RateLimiter;
import com.springbloom.domain.service.ratelimit.RateLimiter.Decision;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP limits on the public chat endpoints. Only /api/chat/** is touched:
 * Stripe's webhook, the cart and the admin must never be throttled.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Duration AUDIT_EVERY = Duration.ofMinutes(1);
    private static final int MAX_AUDIT_KEYS = 10_000;

    private final RateLimitProperties properties;
    private final GuardrailEventRepository events;
    private final Clock clock;
    private final RateLimiter messages;
    private final RateLimiter messagesPerDay;
    private final RateLimiter vision;
    private final RateLimiter quotations;
    private final RateLimiter history;
    private final ConcurrentHashMap<String, Instant> lastAudit = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, GuardrailEventRepository events, Clock storeClock) {
        this.properties = properties;
        this.events = events;
        this.clock = storeClock;
        this.messages = limiter(properties.getMessages());
        this.messagesPerDay = limiter(properties.getMessagesPerDay());
        this.vision = limiter(properties.getVision());
        this.quotations = limiter(properties.getQuotations());
        this.history = limiter(properties.getHistory());
    }

    private RateLimiter limiter(Rule rule) {
        return new RateLimiter(rule.getCapacity(), rule.getRefillPerMinute(), properties.getMaxKeys(), clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !request.getRequestURI().startsWith("/api/chat/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String ip = ClientIp.of(request);
        Decision decision = decide(request, ip);

        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        reject(response, ip, request.getRequestURI(), decision.retryAfter());
    }

    private Decision decide(HttpServletRequest request, String ip) {

        String path = request.getRequestURI();
        boolean post = "POST".equals(request.getMethod());

        if (post && path.equals("/api/chat/messages")) {
            return worst(messages.tryAcquire(ip), messagesPerDay.tryAcquire(ip));
        }
        if (post && path.equals("/api/chat/identify")) {
            return vision.tryAcquire(ip);
        }
        if (post && path.equals("/api/chat/quotations")) {
            return quotations.tryAcquire(ip);
        }
        if ("GET".equals(request.getMethod()) && path.equals("/api/chat/history")) {
            return history.tryAcquire(ip);
        }
        return new Decision(true, Duration.ZERO);
    }

    private static Decision worst(Decision a, Decision b) {

        if (a.allowed() && b.allowed()) {
            return a;
        }
        Duration wait = a.retryAfter().compareTo(b.retryAfter()) >= 0 ? a.retryAfter() : b.retryAfter();
        return new Decision(false, wait);
    }

    private void reject(HttpServletResponse response, String ip, String path, Duration retryAfter)
            throws IOException {

        long seconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
        log.warn("Rate limit hit by {} on {}, retry in {}s", ip, path, seconds);
        audit(ip);

        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(seconds));
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + GuardrailMessages.RATE_LIMITED + "\"}");
    }

    /** At most one row a minute per IP, or the audit becomes the abuse target. */
    private void audit(String ip) {

        Instant now = clock.instant();
        if (lastAudit.size() > MAX_AUDIT_KEYS) {
            lastAudit.entrySet().removeIf(e -> Duration.between(e.getValue(), now).compareTo(AUDIT_EVERY) >= 0);
        }
        Instant previous = lastAudit.get(ip);
        if (previous != null && Duration.between(previous, now).compareTo(AUDIT_EVERY) < 0) {
            return;
        }
        lastAudit.put(ip, now);
        try {
            events.record(null, Stage.INPUT, "rate_limited");
        } catch (RuntimeException failure) {
            log.warn("Could not record rate limit event", failure);
        }
    }
}
