package io.tokentrim.config;

import io.tokentrim.api.Errors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * When {@code TOKENTRIM_PROXY_API_KEY} is set, require it on {@code /v1/**}.
 * Actuator and {@code /healthz} stay open so scrapers and probes do not need the key;
 * keep those ports on a private network.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProxyAuthFilter extends OncePerRequestFilter {

    private final String apiKey;

    public ProxyAuthFilter(TokenTrimProperties properties) {
        String configured = properties.getSecurity().getProxyApiKey();
        this.apiKey = configured == null ? "" : configured;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (apiKey.isBlank()) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String expected = "Bearer " + apiKey;
        byte[] presented = header == null ? new byte[0] : header.getBytes(StandardCharsets.UTF_8);
        byte[] required = expected.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(presented, required)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(Errors.json("Invalid API key", "invalid_request_error"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
