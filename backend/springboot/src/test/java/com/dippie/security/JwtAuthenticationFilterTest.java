package com.dippie.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dippie.user.User;
import com.dippie.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtAuthenticationFilterTest {

    private static final String VALID_SECRET = Base64.getEncoder().encodeToString(sequentialBytes(32));
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWhenUserIsEnabledAndTokenVersionMatches() throws Exception {
        JwtService jwtService = createJwtService();
        UserRepository userRepository = mock(UserRepository.class);
        User user = createUser(10L, "alice", Role.ROLE_USER);
        user.changePassword("encoded-2");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        String token = jwtService.issueLoginToken("alice", user.getTokenVersion());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new Viewer(10L, "alice", Role.ROLE_USER));
    }

    @Test
    void rejectsWhenTokenVersionIsStale() throws Exception {
        JwtService jwtService = createJwtService();
        UserRepository userRepository = mock(UserRepository.class);
        User user = createUser(10L, "alice", Role.ROLE_USER);
        user.changePassword("encoded-2");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        String staleToken = jwtService.issueLoginToken("alice", 0L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + staleToken);
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsWhenUserIsDisabled() throws Exception {
        JwtService jwtService = createJwtService();
        UserRepository userRepository = mock(UserRepository.class);
        User user = createUser(10L, "alice", Role.ROLE_USER);
        user.disable();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        String token = jwtService.issueLoginToken("alice", user.getTokenVersion());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsTokenWithoutVersionClaim() throws Exception {
        JwtService jwtService = createJwtService();
        UserRepository userRepository = mock(UserRepository.class);
        User user = createUser(10L, "alice", Role.ROLE_USER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        String tokenWithoutVer = Jwts.builder()
                .subject("alice")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(VALID_SECRET)), Jwts.SIG.HS256)
                .compact();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithoutVer);
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static JwtService createJwtService() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        JwtProperties properties = new JwtProperties(VALID_SECRET, Duration.ofHours(24), Duration.ofMinutes(5));
        return new JwtService(properties, environment, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User createUser(Long id, String username, Role role) {
        User user = new User(username, username + "@example.com", "encoded", username, role, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static byte[] sequentialBytes(int length) {
        byte[] bytes = new byte[length];
        IntStream.range(0, length).forEach(index -> bytes[index] = (byte) index);
        return bytes;
    }
}
