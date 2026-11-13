package com.creditflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/** Recusa com 429 a origem bloqueada e conta as respostas 401 das requisicoes que trazem credencial. */
public class AuthThrottleFilter extends OncePerRequestFilter {
    private final AuthFailureThrottle throttle;

    public AuthThrottleFilter(AuthFailureThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            chain.doFilter(request, response);
            return;
        }
        String origin = request.getRemoteAddr();
        if (throttle.isBlocked(origin)) {
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, "300");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Muitas tentativas de autenticacao; tente mais tarde\"}");
            return;
        }
        chain.doFilter(request, response);
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            throttle.recordFailure(origin);
        }
    }
}
