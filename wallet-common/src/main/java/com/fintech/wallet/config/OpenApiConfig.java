package com.fintech.wallet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Value("${spring.application.name:fintech-service}")
    private String applicationName;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fintech Wallet & Payment Gateway - " + applicationName.toUpperCase())
                        .description("Tài liệu đặc tả API và giao diện kiểm thử trực tiếp Microservice")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Nguyễn Quốc Tịnh")
                                .email("quoctinhstudy1911@gmail.com")))
                // Mặc định gọi qua API Gateway cổng 80 để đi qua đầy đủ các bộ lọc bảo mật
                .servers(List.of(
                        new Server().url("http://localhost:80").description("API Gateway (Cổng 80)")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dán chuỗi Access Token vào đây (Không cần gõ chữ 'Bearer ')")));
    }
}
