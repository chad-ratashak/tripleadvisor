package org.triplea.generalstaff.llm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A stand-in Ollama server that replays canned answers, so the language layer tests run offline.
 */
final class FakeOllama implements AutoCloseable {
  private final HttpServer server;
  private final Queue<String> answers = new ConcurrentLinkedQueue<>();
  private final List<JsonObject> requests = new ArrayList<>();
  private volatile int statusCode = 200;

  FakeOllama() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/api/tags", exchange -> respond(exchange, 200, "{\"models\":[{\"name\":\"fake:1b\"}]}"));
    server.createContext("/api/chat", this::chat);
    server.start();
  }

  OllamaClient client() {
    return new OllamaClient(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(60));
  }

  /** The next {@code /api/chat} call answers with this content (the model's JSON text). */
  void answerWith(final String content) {
    answers.add(content);
  }

  void failWith(final int status) {
    statusCode = status;
  }

  List<JsonObject> requests() {
    return requests;
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void chat(final HttpExchange exchange) throws IOException {
    final String body =
        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    synchronized (requests) {
      requests.add(JsonParser.parseString(body).getAsJsonObject());
    }
    if (statusCode != 200) {
      respond(exchange, statusCode, "{\"error\":\"simulated failure\"}");
      return;
    }
    final String content = answers.poll();
    if (content == null) {
      respond(exchange, 500, "{\"error\":\"no canned answer left\"}");
      return;
    }
    final JsonObject message = new JsonObject();
    message.addProperty("role", "assistant");
    message.addProperty("content", content);
    final JsonObject response = new JsonObject();
    response.addProperty("model", "fake:1b");
    response.add("message", message);
    response.addProperty("prompt_eval_count", 12);
    response.addProperty("eval_count", 7);
    respond(exchange, 200, response.toString());
  }

  private static void respond(final HttpExchange exchange, final int status, final String body)
      throws IOException {
    final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
