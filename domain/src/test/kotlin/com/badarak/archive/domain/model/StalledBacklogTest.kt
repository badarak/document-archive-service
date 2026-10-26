package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StalledBacklogTest {

    @Test
    fun `an empty backlog has no document and no oldest update`() {
        assertEquals(0, StalledBacklog.EMPTY.count)
        assertNull(StalledBacklog.EMPTY.oldestUpdatedAt)
    }

    @Test
    fun `a negative count is refused`() {
        assertThrows(IllegalArgumentException::class.java) { StalledBacklog(-1, null) }
    }
}
