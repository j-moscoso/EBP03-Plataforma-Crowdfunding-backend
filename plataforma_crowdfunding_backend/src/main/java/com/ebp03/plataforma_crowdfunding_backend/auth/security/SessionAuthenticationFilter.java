package com.ebp03.plataforma_crowdfunding_backend.auth.security;

import com.ebp03.plataforma_crowdfunding_backend.auth.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String SESSION_REQUEST_ATTRIBUTE = "currentSession";
    private final SessionService sessionService;
    private final String cookieName;

    public SessionAuthenticationFilter(SessionService sessionService, @Value("${app.auth.session-cookie-name:AUTH_SESSION}") String cookieName) {
        this.sessionService = sessionService;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = readCookie(request);
        if (token != null) {
            sessionService.findUsable(token).ifPresent(session -> {
                request.setAttribute(SESSION_REQUEST_ATTRIBUTE, session);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(session.getUser(), null, java.util.List.of()));
            });
        }
        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (cookieName.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
