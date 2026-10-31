package org.triplea.generalstaff.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triplea.generalstaff.llm.OllamaClient;

class LocalHttpApiTest {
  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void servesIndexHealthObservationsAndErrors(@TempDir final Path tempDir) throws Exception {
    final Path xml = tempDir.resolve("minimap.xml");
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("minimap.xml")) {
      Files.copy(in, xml);
    }
    final StaffService service =
        new StaffService(
            new OllamaClient(URI.create("http://127.0.0.1:9"), Duration.ofSeconds(1)), "none");
    try (LocalHttpApi api = new LocalHttpApi(0, service)) {
      api.start();
      final String base = "http://127.0.0.1:" + api.port();

      final HttpResponse<String> index = get(base + "/");
      assertThat(index.statusCode()).isEqualTo(200);
      assertThat(index.body()).contains("/observe").contains("/parse-directive");

      final JsonObject health = json(get(base + "/health"));
      assertThat(health.get("status").getAsString()).isEqualTo("ok");
      assertThat(health.get("ollama").getAsBoolean()).isFalse();

      final HttpResponse<String> observe =
          post(base + "/observe", "{\"game\":\"" + xml + "\",\"focus\":[\"blue capitol\"]}");
      assertThat(observe.statusCode()).isEqualTo(200);
      final JsonObject observation = JsonParser.parseString(observe.body()).getAsJsonObject();
      assertThat(observation.get("gameName").getAsString()).isEqualTo("Minimap");
      assertThat(observation.getAsJsonArray("territories").size()).isGreaterThan(1);

      final HttpResponse<String> reach =
          post(base + "/reach", "{\"game\":\"" + xml + "\",\"step\":\"russianCombatMove\"}");
      assertThat(reach.statusCode()).isEqualTo(200);
      assertThat(JsonParser.parseString(reach.body()).getAsJsonObject().get("player").getAsString())
          .isEqualTo("Russians");

      final HttpResponse<String> missing = post(base + "/observe", "{}");
      assertThat(missing.statusCode()).isEqualTo(400);
      assertThat(json(missing).get("error").getAsString()).contains("game");

      final HttpResponse<String> notJson = post(base + "/observe", "[1,2]");
      assertThat(notJson.statusCode()).isEqualTo(400);

      assertThat(post(base + "/nowhere", "{}").statusCode()).isEqualTo(404);
      final HttpResponse<String> noDirective =
          post(base + "/validate-directive", "{\"game\":\"" + xml + "\"}");
      assertThat(noDirective.statusCode()).isEqualTo(400);
      assertThat(noDirective.body()).contains("parsed");
      assertThat(get(base + "/observe").statusCode()).isEqualTo(405);

      final HttpResponse<String> noModel =
          post(base + "/parse-directive", "{\"game\":\"" + xml + "\",\"directive\":\"win\"}");
      assertThat(noModel.statusCode()).isEqualTo(502);
    }
  }

  private HttpResponse<String> get(final String url) throws IOException, InterruptedException {
    return http.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private HttpResponse<String> post(final String url, final String body)
      throws IOException, InterruptedException {
    return http.send(
        HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  @Test
  void hostingRunsOnAPrivateCopyAndOrdersCanBeFiledBeforeTheEngineStarts(
      @TempDir final Path tempDir) throws Exception {
    final Path xml = tempDir.resolve("minimap.xml");
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("minimap.xml")) {
      Files.copy(in, xml);
    }
    final StaffService service =
        new StaffService(
            new OllamaClient(URI.create("http://127.0.0.1:9"), Duration.ofSeconds(1)), "none");
    try (LocalHttpApi api = new LocalHttpApi(0, service)) {
      api.start();
      final String base = "http://127.0.0.1:" + api.port();
      final JsonObject before = json(post(base + "/observe", "{\"game\":\"" + xml + "\"}"));

      final HttpResponse<String> started =
          post(
              base + "/execute/start",
              "{\"game\":\""
                  + xml
                  + "\",\"staff\":[\"Russians\"],\"mode\":\"auto-approve\","
                  + "\"run\":false,\"work\":\""
                  + tempDir.resolve("host")
                  + "\","
                  + "\"steps\":[{\"step\":\"russianCombatMove\",\"orders\":[],\"retreatAfterRound\":-1}]}");
      assertThat(started.statusCode()).as(started.body()).isEqualTo(200);
      assertThat(json(started).get("running").getAsBoolean()).isFalse();
      assertThat(json(started).get("round").getAsInt()).isEqualTo(before.get("round").getAsInt());

      final HttpResponse<String> nothingPending =
          post(base + "/execute/approve", "{\"by\":\"test\",\"step\":\"russianCombatMove\"}");
      assertThat(json(nothingPending).get("approved").getAsBoolean()).isFalse();
      assertThat(json(nothingPending).get("reason").getAsString()).contains("nothing is waiting");

      final HttpResponse<String> run = post(base + "/execute/run", "{\"lastRound\":1}");
      assertThat(run.statusCode()).as(run.body()).isEqualTo(200);
      JsonObject status = json(run);
      final long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
      while (status.get("running").getAsBoolean() && System.nanoTime() < deadline) {
        Thread.sleep(200);
        status = json(post(base + "/execute/status", "{}"));
      }
      assertThat(status.get("running").getAsBoolean()).as(status.toString()).isFalse();
      assertThat(status.get("round").getAsInt() > 1 || status.get("gameOver").getAsBoolean())
          .as(status.toString())
          .isTrue();
      assertThat(post(base + "/execute/report", "{}").statusCode()).isEqualTo(200);

      // The game the API observes is untouched by the hosted copy.
      final JsonObject after = json(post(base + "/observe", "{\"game\":\"" + xml + "\"}"));
      assertThat(after.get("round").getAsInt()).isEqualTo(before.get("round").getAsInt());
      assertThat(after.get("step")).isEqualTo(before.get("step"));
      assertThat(post(base + "/execute/stop", "{}").statusCode()).isEqualTo(200);
    }
  }

  private static JsonObject json(final HttpResponse<String> response) {
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }
}
