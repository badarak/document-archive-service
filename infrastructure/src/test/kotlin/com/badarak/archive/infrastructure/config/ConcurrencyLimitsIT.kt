package com.badarak.archive.infrastructure.config

import com.badarak.archive.infrastructure.adapter.storage.S3StorageProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = ["ARCHIVE_MAX_THREADS=12"])
@ActiveProfiles("test")
class ConcurrencyLimitsIT {

    @Autowired
    private lateinit var tomcat: TomcatServerProperties

    @Autowired
    private lateinit var storage: S3StorageProperties

    @Test
    fun `the storage connections follow the number of request threads`() {
        assertEquals(12, tomcat.threads.max)
        assertEquals(12, storage.maxConnections)
    }
}
