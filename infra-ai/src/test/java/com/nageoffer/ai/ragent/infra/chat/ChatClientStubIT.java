package com.nageoffer.ai.ragent.infra.chat;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chat client integration test. The container is a local HTTP stub, not a model provider.
 */
@Testcontainers
class ChatClientStubIT {

    @Container
    static final GenericContainer<?> STUB = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
            .withExposedPorts(8080)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("wiremock/mappings/chat.json"),
                    "/home/wiremock/mappings/chat.json")
            .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    @Test
    void readsTheStubAnswerWithoutCallingAModelProvider() {
        OllamaChatClient client = new OllamaChatClient();
        ReflectionTestUtils.setField(client, "syncHttpClient", new OkHttpClient());

        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl("http://" + STUB.getHost() + ":" + STUB.getMappedPort(8080));
        provider.setEndpoints(Map.of("chat", "/v1/chat/completions"));

        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setModel("stub-model");

        String answer = client.chat(
                ChatRequest.builder()
                        .messages(List.of(new ChatMessage(ChatMessage.Role.USER, "hello")))
                        .build(),
                new ModelTarget("stub", candidate, provider));

        assertThat(answer).isEqualTo("stub-answer");
    }
}
