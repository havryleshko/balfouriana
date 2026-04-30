package com.balfouriana.api

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DemoControllerIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `lists demo scenarios`() {
        val response = mockMvc.perform(get("/demo/scenarios"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        assertTrue(response.contains("01-clean-auto-file"))
    }

    @Test
    fun `serves demo scenario file`() {
        mockMvc.perform(get("/demo/scenarios/01-clean-auto-file/halkin-eod-2026-04-30.csv"))
            .andExpect(status().isOk)
    }
}
