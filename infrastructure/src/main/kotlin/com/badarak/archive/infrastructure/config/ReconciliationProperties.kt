package com.badarak.archive.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("archive.reconciliation")
class ReconciliationProperties(
    val enabled: Boolean = true,
    val interval: Duration = Duration.ofMinutes(5),
    val gracePeriod: Duration = Duration.ofMinutes(15),
    val batchSize: Int = 100,
)
