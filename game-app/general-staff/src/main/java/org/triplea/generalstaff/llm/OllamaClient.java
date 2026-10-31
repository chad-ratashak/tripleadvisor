package org.triplea.generalstaff.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A small client for a local Ollama server's REST API: {@code /api/chat} with a JSON schema as the
 * response format, and {@code /api/tags} to list models. Only the language side of the General
 * Staff talks to it; nothing it returns is trusted until code has validated it.
 */
public final class OllamaClient {
  public static final String DEFAULT_HOST = "http://localhost:11434";
  public static final String HOST_ENVIRONMENT_VARIABLE = "OLLAMA_HOST";

  /** The model's answer and the server's accounting for it. */
  public record ChatResult(
      String model, String content, long latencyMillis, int promptTokens, int outputTokens) {}

  private final URI baseUri;
  private final HttpClient http;
  private final Duration requestTimeout;
  private final Gson gson = new Gson();

  public OllamaClient(final URI baseUri, final Duration requestTimeout) {
    this.baseUri = baseUri;
    this.requestTimeout = requestTimeout;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  /** Uses {@code OLLAMA_HOST} when set, else the server's default address. */
  public static OllamaClient fromEnvironment() {
    final String host =
        Optional.ofNullable(System.getenv(HOST_ENVIRONMENT_VARIABLE))
            .filter(value -> !value.isBlank())
            .map(value -> value.contains("://") ? value : "http://" + value)
            .orElse(DEFAULT_HOST);
    return new OllamaClient(URI.create(host), Duration.ofMinutes(5));
  }

  public URI baseUri() {
    return baseUri;
  }

  /** The models the server has, or an empty list when it cannot be reached. */
  public List<String> models() {
    try {
      final HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(baseUri.resolve("/api/tags"))
                  .timeout(Duration.ofSeconds(10))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        return List.of();
      }
      final List<String> names = new ArrayList<>();
      final JsonArray models =
          JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("models");
      for (final JsonElement model : models) {
        names.add(model.getAsJsonObject().get("name").getAsString());
      }
      return names;
    } catch (final IOException | JsonSyntaxException | IllegalStateException e) {
      return List.of();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return List.of();
    }
  }

  public boolean isAvailable() {
    return !models().isEmpty();
  }

  /**
   * One chat turn whose answer must follow {@code schema} (Ollama's structured output). Temperature
   * 0 makes parsing repeatable; explanations may ask for more.
   */
  public ChatResult chatJson(
      final String model,
      final String systemPrompt,
      final String userPrompt,
      final JsonObject schema,
      final double temperature) {
    final JsonObject request = new JsonObject();
    request.addProperty("model", model);
    request.addProperty("stream", false);
    request.add("format", schema);
    final JsonObject options = new JsonObject();
    options.addProperty("temperature", temperature);
    request.add("options", options);
    final JsonArray messages = new JsonArray();
    messages.add(message("system", systemPrompt));
    messages.add(message("user", userPrompt));
    request.add("messages", messages);

    final long started = System.nanoTime();
    final JsonObject response = post("/api/chat", request);
    final long latencyMillis = (System.nanoTime() - started) / 1_000_000;
    final JsonObject message = response.getAsJsonObject("message");
    if (message == null || !message.has("content")) {
      throw new OllamaException("The model returned no message content: " + response);
    }
    return new ChatResult(
        model,
        message.get("content").getAsString(),
        latencyMillis,
        intField(response, "prompt_eval_count"),
        intField(response, "eval_count"));
  }

  private JsonObject post(final String path, final JsonObject body) {
    final HttpRequest request =
        HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
            .build();
    try {
      final HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        throw new OllamaException(
            "Ollama answered HTTP "
                + response.statusCode()
                + " for "
                + path
                + ": "
                + response.body());
      }
      return JsonParser.parseString(response.body()).getAsJsonObject();
    } catch (final IOException e) {
      throw new OllamaException("Could not reach Ollama at " + baseUri + ": " + e.getMessage(), e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OllamaException("Interrupted while waiting for Ollama", e);
    } catch (final JsonSyntaxException | IllegalStateException e) {
      throw new OllamaException("Ollama returned something that is not a JSON object", e);
    }
  }

  private static JsonObject message(final String role, final String content) {
    final JsonObject message = new JsonObject();
    message.addProperty("role", role);
    message.addProperty("content", content);
    return message;
  }

  private static int intField(final JsonObject object, final String name) {
    return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsInt() : 0;
  }
}
