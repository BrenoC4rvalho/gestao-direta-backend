package br.com.gestaodireta.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.gestaodireta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SystemStatusControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldReturnPublicSystemStatus() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/api/system/status").contextPath(CONTEXT_PATH))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").exists())
                        .andExpect(jsonPath("$.application").exists())
                        .andExpect(jsonPath("$.profile").exists())
                        .andExpect(jsonPath("$.database").exists())
                        .andExpect(jsonPath("$.uptimeSeconds").exists())
                        .andExpect(jsonPath("$.timestamp").exists())
                        .andReturn();

        String content = result.getResponse().getContentAsString();

        assertThat(content)
                .doesNotContain("jdbc:")
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("username")
                .doesNotContain("Exception");
    }

    @Test
    void shouldExposePublicActuatorHealthAndInfo() throws Exception {
        mockMvc.perform(get("/api/actuator/health").contextPath(CONTEXT_PATH))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/actuator/info").contextPath(CONTEXT_PATH))
                .andExpect(status().isOk());
    }

    @Test
    void shouldProtectActuatorMetrics() throws Exception {
        mockMvc.perform(get("/api/actuator/metrics").contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/actuator/metrics")
                                .contextPath(CONTEXT_PATH)
                                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
