package com.badarak.archive.infrastructure.validation

import com.badarak.archive.domain.model.DocumentFormatPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(DocumentValidationProperties::class)
class DocumentValidationConfiguration {

    @Bean
    fun documentFormatPolicy(properties: DocumentValidationProperties): DocumentFormatPolicy =
        DocumentFormatPolicy.ofExtensions(properties.allowedExtensions)
}
