package com.example.razorpaywebhook.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Razorpay Webhook Processor API")
                        .version("1.0.0")
                        .description("""
                                Production-grade distributed payment webhook processor.
                                Handles Razorpay webhook ingestion, double-entry ledger,
                                fraud detection, audit hash chain, and settlement reporting.
                                
                                All endpoints except POST /webhooks/razorpay and
                                GET /actuator/health require Bearer JWT authentication.
                                """)
                        .contact(new Contact()
                                .name("Utkarsh Kumar Singh")
                                .url("https://github.com/skywalker-4567")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token from POST /auth/login")));
    }
}