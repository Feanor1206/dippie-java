package com.dippie.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Khai báo thông tin OpenAPI và nút nhập Bearer JWT cho Swagger UI.
@Configuration
class OpenApiConfig {

    static final String API_VERSION = "1.0.0";
    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    OpenAPI dippieOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dippie API")
                        .version(API_VERSION)
                        .description("Tài liệu API cho ứng dụng xem phim hoạt hình Dippie."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_AUTH_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ));
    }
}
