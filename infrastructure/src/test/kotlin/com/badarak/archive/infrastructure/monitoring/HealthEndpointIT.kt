package com.badarak.archive.infrastructure.monitoring

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `the health endpoint reports the service as up without exposing details`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
            jsonPath("$.components") { doesNotExist() }
        }
    }

    @Test
    fun `the liveness and readiness probes are exposed`() {
        mockMvc.get("/actuator/health/liveness").andExpect { status { isOk() } }
        mockMvc.get("/actuator/health/readiness").andExpect { status { isOk() } }
    }

    @Test
    fun `other actuator paths are not open to anonymous callers`() {
        mockMvc.get("/actuator/info").andExpect { status { isUnauthorized() } }
        mockMvc.get("/actuator/env").andExpect { status { isUnauthorized() } }
    }
}
