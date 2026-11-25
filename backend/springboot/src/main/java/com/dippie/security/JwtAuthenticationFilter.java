package com.dippie.security;

import com.dippie.user.User;
import com.dippie.user.UserRepository;
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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

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
                jwtService.parseLoginToken(token).ifPresent(loginToken ->
                        userRepository.findByUsername(loginToken.username())
                                .filter(user -> user.isEnabled() && user.getTokenVersion() == loginToken.tokenVersion())
                                .ifPresent(this::authenticate)
                );
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(User user) {
        Viewer viewer = new Viewer(user.getId(), user.getUsername(), user.getRole());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                viewer,
                null,
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
