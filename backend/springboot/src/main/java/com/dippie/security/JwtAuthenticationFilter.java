package com.dippie.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Filter đọc Bearer JWT một lần mỗi request rồi nạp Viewer vào SecurityContext nếu tài khoản còn hoạt động.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ActiveViewerLookup viewerLookup;

    public JwtAuthenticationFilter(JwtService jwtService, ActiveViewerLookup viewerLookup) {
        this.jwtService = jwtService;
        this.viewerLookup = viewerLookup;
    }

    // Tách token từ header Authorization, kiểm tra chữ ký và tokenVersion trong DB trước khi cho đi tiếp.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                jwtService.parseLoginToken(token)
                        .flatMap(loginToken -> viewerLookup.findActiveViewer(
                                loginToken.username(), loginToken.tokenVersion()))
                        .ifPresent(this::authenticate);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(Viewer viewer) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                viewer,
                null,
                List.of(new SimpleGrantedAuthority(viewer.role().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
