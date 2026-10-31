package org.triplea.generalstaff.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OllamaClientTest {
  @Test
  void listsModelsAndChatsWithASchema() throws IOException {
    try (FakeOllama ollama = new FakeOllama()) {
      final OllamaClient client = ollama.client();
      ollama.answerWith("{\"answer\":42}");

      assertThat(client.models()).containsExactly("fake:1b");
      assertThat(client.isAvailable()).isTrue();
      final OllamaClient.ChatResult result =
          client.chatJson("fake:1b", "system words", "user words", IntentParser.schema(), 0.0);

      assertThat(result.content()).isEqualTo("{\"answer\":42}");
      assertThat(result.promptTokens()).isEqualTo(12);
      assertThat(result.outputTokens()).isEqualTo(7);
      assertThat(result.model()).isEqualTo("fake:1b");
      final JsonObject request = ollama.requests().get(0);
      assertThat(request.get("model").getAsString()).isEqualTo("fake:1b");
      assertThat(request.get("stream").getAsBoolean()).isFalse();
      assertThat(request.getAsJsonObject("format").get("type").getAsString()).isEqualTo("object");
      assertThat(request.getAsJsonArray("messages")).hasSize(2);
      assertThat(request.getAsJsonObject("options").get("temperature").getAsDouble()).isZero();
    }
  }

  @Test
  void serverErrorsBecomeOllamaExceptions() throws IOException {
    try (FakeOllama ollama = new FakeOllama()) {
      ollama.failWith(500);
      final OllamaClient client = ollama.client();

      assertThatThrownBy(() -> client.chatJson("fake:1b", "s", "u", IntentParser.schema(), 0.0))
          .isInstanceOf(OllamaException.class)
          .hasMessageContaining("500");
    }
  }

  @Test
  void anUnreachableServerIsReportedNotHidden() {
    final OllamaClient client =
        new OllamaClient(URI.create("http://127.0.0.1:9"), Duration.ofSeconds(2));

    assertThat(client.models()).isEmpty();
    assertThat(client.isAvailable()).isFalse();
    assertThatThrownBy(() -> client.chatJson("fake:1b", "s", "u", IntentParser.schema(), 0.0))
        .isInstanceOf(OllamaException.class);
  }

  @Test
  void hostFromEnvironmentDefaultsToLocalhost() {
    assertThat(OllamaClient.fromEnvironment().baseUri().toString()).startsWith("http://");
  }
}
