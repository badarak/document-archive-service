package com.badarak.archive.infrastructure.security

import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher

@Configuration
class SecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity, errorHandler: ProblemDetailsSecurityErrorHandler): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize(DispatcherTypeRequestMatcher(DispatcherType.ERROR), permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize("/v3/api-docs", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize(HttpMethod.GET, DOCUMENTS, hasAuthority(READ_DOCUMENTS))
                authorize(HttpMethod.POST, DOCUMENT_COLLECTION, hasAuthority(WRITE_DOCUMENTS))
                authorize(HttpMethod.DELETE, DOCUMENT, hasAuthority(DELETE_DOCUMENTS))
                authorize(anyRequest, denyAll)
            }
            oauth2ResourceServer {
                jwt { }
                authenticationEntryPoint = errorHandler
                accessDeniedHandler = errorHandler
            }
            exceptionHandling {
                authenticationEntryPoint = errorHandler
                accessDeniedHandler = errorHandler
            }
        }
        return http.build()
    }

    companion object {
        private const val DOCUMENT_COLLECTION = "/api/v1/documents"
        private const val DOCUMENT = "$DOCUMENT_COLLECTION/*"
        private const val DOCUMENTS = "$DOCUMENT_COLLECTION/**"
        private const val READ_DOCUMENTS = "SCOPE_documents:read"
        private const val WRITE_DOCUMENTS = "SCOPE_documents:write"
        private const val DELETE_DOCUMENTS = "SCOPE_documents:delete"
    }
}
