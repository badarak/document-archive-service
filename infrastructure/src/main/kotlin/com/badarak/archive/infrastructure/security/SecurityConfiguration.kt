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
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            // Callers send a bearer token on every request: no session, hence nothing for CSRF to protect.
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                // Error rendering happens after the original request was authorized; securing it
                // again would turn every failure into a 401.
                authorize(DispatcherTypeRequestMatcher(DispatcherType.ERROR), permitAll)
                // Monitoring probes carry no token.
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize(HttpMethod.GET, DOCUMENTS, hasAuthority(READ_DOCUMENTS))
                authorize(DOCUMENTS, hasAuthority(WRITE_DOCUMENTS))
                // Anything not listed above is closed, so a new endpoint is never exposed by omission.
                authorize(anyRequest, denyAll)
            }
            oauth2ResourceServer { jwt { } }
        }
        return http.build()
    }

    companion object {
        private const val DOCUMENTS = "/api/v1/documents/**"

        // Spring Security maps each scope of the token to an authority with this prefix.
        private const val READ_DOCUMENTS = "SCOPE_documents:read"
        private const val WRITE_DOCUMENTS = "SCOPE_documents:write"
    }
}
