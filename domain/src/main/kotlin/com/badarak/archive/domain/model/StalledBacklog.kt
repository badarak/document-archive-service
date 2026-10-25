package com.badarak.archive.domain.model

import java.time.Instant

/**
 * The documents left in a status for longer than the grace period: how many, and when the oldest was last updated, or null
 * when there is none. A backlog that grows, or whose oldest document keeps ageing, means the reconciliation is not keeping
 * up or is blocked.
 */
data class StalledBacklog(val count: Long, val oldestUpdatedAt: Instant?) {

    init {
        require(count >= 0) { "The count of stalled documents must not be negative, got $count" }
    }

    companion object {
        val EMPTY = StalledBacklog(0, null)
    }
}
