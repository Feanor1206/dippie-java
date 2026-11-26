package com.dippie.security;

import com.dippie.common.web.ApiResponse;
import com.dippie.model.user.Role;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

// cấu hình Spring Security không lưu session, phân quyền kiểu fail-closed, gắn header phòng thủ và bật CORS cùng BCrypt
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; "
                    + "script-src 'self' https://www.youtube.com https://s.ytimg.com; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data: blob: https://i.ytimg.com https://img.youtube.com https://*.ytimg.com; "
                    + "font-src 'self' data:; "
                    + "frame-src 'self' https://www.youtube-nocookie.com https://www.youtube.com; "
                    + "connect-src 'self' https://www.youtube.com https://www.youtube-nocookie.com; "
                    + "media-src 'self' blob:; worker-src 'self' blob:";
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";
    private static final String ADMIN_FORBIDDEN_MESSAGE = "Chỉ tài khoản quản trị mới dùng được mục này.";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RequestBodySizeLimitFilter requestBodySizeLimitFilter;
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RequestBodySizeLimitFilter requestBodySizeLimitFilter,
            CorsProperties corsProperties,
            ObjectMapper objectMapper
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.requestBodySizeLimitFilter = requestBodySizeLimitFilter;
        this.corsProperties = corsProperties;
        this.objectMapper = objectMapper;
    }

    // chuỗi filter bảo mật chính, Việt nhớ ở đây để anyRequest().authenticated() nha
    // sau này mở thêm API công khai nào phải khai báo rõ ở đây kẻo bị chặn 401
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        // tắt Cache-Control mặc định của Spring kẻo ghi đè cache dài hạn của file tĩnh, chỉ gắn no-store cho API nhạy cảm
                        .cacheControl(cache -> cache.disable())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31_536_000L)
                                .includeSubDomains(true))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY))
                        .addHeaderWriter(noStoreHeaderWriter())
                        .addObjectPostProcessor(eagerHeaderWriting()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // nhóm route quản trị và route riêng của user đang đăng nhập
                        .requestMatchers("/api/admin/**").hasAuthority(Role.ROLE_ADMIN.name())
                        .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/users/**").authenticated()
                        // mấy route API và tài liệu công khai được mở rõ ràng
                        .requestMatchers(HttpMethod.GET,
                                "/api/health",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/api/movies",
                                "/api/movies/**",
                                "/api/catalog/**",
                                "/api/media/stream/**",
                                "/api/assistant/status",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/media/stream/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm",
                                "/api/media/grant/**",
                                "/api/recommend",
                                "/api/chat",
                                "/v1/search",
                                "/v1/discover",
                                "/api/reports/playback"
                        ).permitAll()
                        .requestMatchers("/error").permitAll()
                        // mở GET/HEAD cho SPA và file tĩnh ngoài mấy prefix API/Actuator
                        .requestMatchers(frontendRouteMatcher()).permitAll()
                        // mặc định fail-closed, route hay HTTP method nào chưa liệt kê thì đều bắt đăng nhập
                        .anyRequest().authenticated())
                .addFilterBefore(requestBodySizeLimitFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // khớp GET/HEAD tới SPA hoặc file tĩnh nằm ngoài prefix API, Actuator và Swagger
    private static RequestMatcher frontendRouteMatcher() {
        return request -> {
            String method = request.getMethod();
            if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
                return false;
            }
            String path = normalizedPath(request);
            return !path.startsWith("/api/")
                    && !"/api".equals(path)
                    && !path.startsWith("/v1/")
                    && !"/v1".equals(path)
                    && !path.startsWith("/v3/")
                    && !"/v3".equals(path)
                    && !path.startsWith("/actuator/")
                    && !"/actuator".equals(path)
                    && !path.startsWith("/swagger-ui");
        };
    }

    // chưa đăng nhập mà gọi /api/admin/** thì trả 403 JSON, còn mấy route cần xác thực khác trả 401 JSON
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            String path = normalizedPath(request);
            if (path.startsWith("/api/admin/")) {
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, ADMIN_FORBIDDEN_MESSAGE);
                return;
            }
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Bạn cần đăng nhập để tiếp tục.");
        };
    }

    // đã đăng nhập mà không có quyền admin hoặc đang ở hồ sơ trẻ em thì trả 403 JSON tiếng Việt
    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean childProfile = authentication != null
                    && authentication.getPrincipal() instanceof Viewer viewer
                    && viewer.isChildProfile();
            String message = childProfile
                    ? "Hồ sơ trẻ em không thể truy cập khu vực quản trị."
                    : ADMIN_FORBIDDEN_MESSAGE;
            writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, message);
        };
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try (PrintWriter writer = response.getWriter()) {
            objectMapper.writeValue(writer, ApiResponse.failure(message));
        }
    }

    // gắn Cache-Control: no-store cho route auth, users, admin hoặc request có Authorization, trừ /api/chat và file tĩnh
    private static HeaderWriter noStoreHeaderWriter() {
        return (HttpServletRequest request, HttpServletResponse response) -> {
            String path = normalizedPath(request);
            if ("/api/chat".equals(path)
                    || path.startsWith("/assets/")
                    || path.startsWith("/illustrations/")) {
                return;
            }
            boolean sensitivePath = path.startsWith("/api/auth/")
                    || path.startsWith("/api/users/")
                    || path.startsWith("/api/admin/");
            boolean hasAuthHeader = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
            if (sensitivePath || hasAuthHeader) {
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            }
        };
    }

    private static String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri;
    }

    // ghi header bảo mật sớm trước khi response commit để mấy endpoint stream vẫn đủ header
    private static ObjectPostProcessor<HeaderWriterFilter> eagerHeaderWriting() {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends HeaderWriterFilter> O postProcess(O filter) {
                filter.setShouldWriteHeadersEagerly(true);
                return filter;
            }
        };
    }

    // mở CORS cho mấy origin đã cấu hình, cho phép kèm cookie và header Authorization
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // tắt tự đăng ký ở servlet container vì JwtAuthenticationFilter là @Component
    // Việt đừng xóa bean này nha, xóa là filter chạy 2 lần mỗi request
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterServletRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // tắt tự đăng ký ở servlet container để RequestBodySizeLimitFilter chỉ chạy 1 lần đúng thứ tự trong chuỗi Security
    @Bean
    public FilterRegistrationBean<RequestBodySizeLimitFilter> bodyLimitFilterServletRegistration(
            RequestBodySizeLimitFilter filter
    ) {
        FilterRegistrationBean<RequestBodySizeLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
