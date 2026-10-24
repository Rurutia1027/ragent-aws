package com.nageoffer.ai.ragent.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP server integration test. The server exposes tools only; it does not call a model.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void servesTheMcpEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://127.0.0.1:" + port + "/mcp", String.class);

        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }
}
