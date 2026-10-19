package com.badarak.archive.infrastructure.journey

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Clock of the journey tests, set by each test so that creation dates are known in advance. */
class MutableClock(@Volatile private var now: Instant) : Clock() {

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    fun set(instant: Instant) {
        now = instant
    }
}
