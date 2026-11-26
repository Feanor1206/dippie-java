package com.dippie.security;

import com.dippie.model.user.Role;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// filter đọc Bearer JWT 1 lần mỗi request rồi nạp Viewer vào SecurityContext nếu tài khoản còn bật
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ActiveViewerLookup viewerLookup;

    public JwtAuthenticationFilter(JwtService jwtService, ActiveViewerLookup viewerLookup) {
        this.jwtService = jwtService;
        this.viewerLookup = viewerLookup;
    }

    // tách token từ header Authorization, check chữ ký và tokenVersion trong DB xong mới cho đi tiếp
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
                                loginToken.username(), loginToken.tokenVersion(), loginToken.profileId()))
                        .ifPresent(this::authenticate);
            }
        }
        filterChain.doFilter(request, response);
    }

    // đang ở hồ sơ trẻ em thì hạ authority xuống ROLE_USER để Security chặn luôn mọi route /api/admin/**
    private void authenticate(Viewer viewer) {
        Role effectiveRole = viewer.isChildProfile() ? Role.ROLE_USER : viewer.role();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                viewer,
                null,
                List.of(new SimpleGrantedAuthority(effectiveRole.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
