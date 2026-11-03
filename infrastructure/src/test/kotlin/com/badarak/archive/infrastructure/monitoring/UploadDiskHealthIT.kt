package com.badarak.archive.infrastructure.monitoring

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(properties = ["management.health.diskspace.threshold=100000TB"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadDiskHealthIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `an instance whose upload disk is full stops receiving traffic but is not restarted`() {
        mockMvc.get("/readyz").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.status") { value("DOWN") }
        }
        mockMvc.get("/livez").andExpect { status { isOk() } }
    }
}
