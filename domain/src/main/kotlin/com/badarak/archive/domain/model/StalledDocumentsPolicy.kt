package com.badarak.archive.domain.model

import java.time.Duration
import java.time.Instant

/**
 * When a document left in progress is considered stalled, and how many such documents one reconciliation run handles. A
 * document last updated within the [gracePeriod] may still be in progress, so it is left alone.
 */
class StalledDocumentsPolicy(val gracePeriod: Duration, val batchSize: Int) {

    init {
        require(!gracePeriod.isNegative) { "The grace period must not be negative, got $gracePeriod" }
        require(batchSize > 0) { "The batch size must be positive, got $batchSize" }
    }

    fun stalledBefore(now: Instant): Instant = now.minus(gracePeriod)
}
