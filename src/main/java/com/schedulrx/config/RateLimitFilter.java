package com.schedulrx.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter implements Filter {

    private static final int MAX_REQUESTS = 10;          // max requests allowed
    private static final long WINDOW_MS   = 60_000;      // per 60 seconds

    // Stores request count + window start time per IP
    private final Map<String, long[]> ipData = new ConcurrentHashMap<>();
    // long[0] = request count, long[1] = window start timestamp

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // Only rate limit the API
        if (!req.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String ip  = req.getRemoteAddr();
        long   now = System.currentTimeMillis();

        // Get or create entry for this IP
        ipData.putIfAbsent(ip, new long[]{0, now});
        long[] data = ipData.get(ip);

        synchronized (data) {
            long windowStart = data[1];
            long count       = data[0];

            // Window expired — reset
            if (now - windowStart > WINDOW_MS) {
                data[0] = 1;
                data[1] = now;
                chain.doFilter(request, response);
                return;
            }

            // Within window — check limit
            if (count >= MAX_REQUESTS) {
                resp.setStatus(429);
                resp.setContentType("application/json");
                resp.getWriter().write(
                        "{\"error\": \"Too many requests. Max " + MAX_REQUESTS +
                                " requests per minute allowed.\"}"
                );
                return;
            }

            // Under limit — allow and increment
            data[0]++;
        }

        chain.doFilter(request, response);
    }
}