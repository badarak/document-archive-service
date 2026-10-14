package com.badarak.archive.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("archive.documents")
class ArchivingProperties(val allowedExtensions: List<String>)
