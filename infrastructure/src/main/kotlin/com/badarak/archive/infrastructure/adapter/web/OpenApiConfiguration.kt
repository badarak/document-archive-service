package com.badarak.archive.infrastructure.adapter.web

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Document Archive Service")
                .version("v1")
                .description("Archives documents for other services: content in S3-compatible storage, metadata in PostgreSQL."),
        )
        .components(
            Components().addSecuritySchemes(
                BEARER_JWT,
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
            ),
        )

    companion object {
        const val BEARER_JWT = "bearer-jwt"
    }
}
