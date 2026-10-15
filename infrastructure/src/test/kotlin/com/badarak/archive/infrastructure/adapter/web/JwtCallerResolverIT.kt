package com.badarak.archive.infrastructure.adapter.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class JwtCallerResolverIT {

    @Autowired
    private lateinit var claims: CallerClaimsProperties

    @Test
    fun `the caller is read from the tenant_id and azp claims by default`() {
        assertEquals("tenant_id", claims.tenant)
        assertEquals("azp", claims.source)
    }
}
