package com.LastBite.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình OpenAPI 3.0 / Swagger UI.
 * <p>
 * Cung cấp scheme xác thực JWT Bearer toàn cục để các endpoint được bảo vệ
 * hiển thị biểu tượng khóa và nhận token từ Swagger UI.
 */
@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI lastBiteOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LastBite API")
                        .description("LastBite — Food Rescue Platform API Documentation")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LastBite Team")
                                .email("lastbite.exe201@gmail.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://api-staging.lastbite.vn").description("Staging"),
                        new Server().url("https://api.lastbite.vn").description("Production")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT access token here")
                                        .name(null)));
    }
}
