package com.badarak.archive.infrastructure.adapter.web

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ProblemDetail
import org.springframework.http.MediaType as SpringMediaType

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

    /**
     * Completes every operation with what they all share, so that the API interface only declares what is specific to
     * each: the 401 and 500 answers, and the Problem Details body of every error answer, which springdoc would otherwise
     * describe with the operation's success type.
     */
    @Bean
    fun problemResponses(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val problemSchemas = ModelConverters.getInstance(true).read(ProblemDetail::class.java)
        problemSchemas.forEach { (name, schema) -> openApi.components.addSchemas(name, schema) }
        openApi.paths.values.flatMap { it.readOperations() }.forEach(::completeResponses)
    }

    private fun completeResponses(operation: Operation) {
        val responses = operation.responses
        responses.putIfAbsent("401", ApiResponse().description("Missing, invalid or expired access token"))
        responses.putIfAbsent("500", ApiResponse().description("Unexpected error"))
        responses.filterKeys { it.first() in "45" }.values.forEach { it.content = problem() }
        operation.responses = ApiResponses().apply { responses.toSortedMap().forEach(::addApiResponse) }
    }

    private fun problem(): Content =
        Content().addMediaType(SpringMediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType().schema(Schema<Any>().`$ref`("ProblemDetail")))

    companion object {
        const val BEARER_JWT = "bearer-jwt"
    }
}
