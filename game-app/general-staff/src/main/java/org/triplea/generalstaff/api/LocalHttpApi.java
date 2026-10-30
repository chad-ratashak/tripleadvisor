package org.triplea.generalstaff.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import games.strategy.engine.data.GameData;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.triplea.generalstaff.doctrine.DoctrineReconciler;
import org.triplea.generalstaff.doctrine.DoctrineStore;
import org.triplea.generalstaff.json.Json;
import org.triplea.generalstaff.llm.IntentParser;
import org.triplea.generalstaff.llm.OllamaException;
import org.triplea.generalstaff.llm.ParsedDirective;
import org.triplea.generalstaff.llm.StrategicDirective;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.plan.OperationParisBaseline;
import org.triplea.generalstaff.plan.OperationParisReport;
import org.triplea.generalstaff.plan.PlanReport;
import org.triplea.generalstaff.staff.ApprovalGate;
import org.triplea.generalstaff.staff.ExecutionSession;
import org.triplea.generalstaff.staff.PlannedOrder;
import org.triplea.generalstaff.staff.PlannedStep;

/**
 * A small JSON-over-HTTP front door to the {@link StaffService}, on the loopback interface only. It
 * exists so a web command UI, a Python experiment or a shell script can talk to the General Staff
 * without being in the JVM. Endpoints are listed in {@link ApiText#INDEX}.
 */
public final class LocalHttpApi implements AutoCloseable {
  private static final Gson GSON = Json.GSON;
  private static final String JSON = "application/json; charset=utf-8";
  private static final String TEXT = "text/plain; charset=utf-8";
  private static final int DEFAULT_RUNS = 1000;
  private static final int DEFAULT_TRAJECTORIES = 40;

  private final HttpServer server;
  private final ExecutorService executor = Executors.newFixedThreadPool(2);
  private final StaffService service;

  /** Binds to {@code port} on the loopback interface; 0 picks a free port. */
  public LocalHttpApi(final int port, final StaffService service) throws IOException {
    this.service = service;
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    server.setExecutor(executor);
    server.createContext("/", this::handle);
  }

  public void start() {
    server.start();
  }

  public int port() {
    return server.getAddress().getPort();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  private void handle(final HttpExchange exchange) throws IOException {
    try {
      final String path = exchange.getRequestURI().getPath();
      final String method = exchange.getRequestMethod();
      if ("GET".equals(method) && "/".equals(path)) {
        respond(exchange, 200, ApiText.INDEX, TEXT);
      } else if ("GET".equals(method) && (path.equals("/ui") || path.startsWith("/ui/"))) {
        serveUi(exchange, path);
      } else if ("GET".equals(method) && "/health".equals(path)) {
        respond(exchange, 200, GSON.toJson(health()), JSON);
      } else if (!"POST".equals(method)) {
        respond(exchange, 405, error(ApiText.METHOD_NOT_ALLOWED), JSON);
      } else {
        final JsonObject body = readBody(exchange.getRequestBody());
        final Optional<Object> result = dispatch(path, body);
        if (result.isEmpty()) {
          respond(exchange, 404, error(String.format(Locale.ROOT, ApiText.NOT_FOUND, path)), JSON);
        } else {
          respond(exchange, 200, GSON.toJson(result.get()), JSON);
        }
      }
    } catch (final IllegalArgumentException | IllegalStateException e) {
      respond(exchange, 400, error(e.getMessage()), JSON);
    } catch (final OllamaException e) {
      respond(exchange, 502, error(e.getMessage()), JSON);
    } catch (final RuntimeException e) {
      respond(exchange, 500, error(e.toString()), JSON);
    } finally {
      exchange.close();
    }
  }

  private Optional<Object> dispatch(final String path, final JsonObject body) throws IOException {
    return switch (path) {
      case "/observe" -> Optional.of(service.observe(game(body), strings(body, "focus")));
      case "/history" -> Optional.of(service.history(game(body)));
      case "/legal" ->
          Optional.of(
              service.legal(
                  game(body),
                  string(body, "step"),
                  string(body, "from"),
                  string(body, "to"),
                  string(body, "type"),
                  integer(body, "count", 1)));
      case "/reach" ->
          Optional.of(
              service.reach(game(body), string(body, "step"), optionalString(body, "from")));
      case "/odds/paris" ->
          Optional.of(
              service.paris(
                  game(body),
                  integer(body, "runs", DEFAULT_RUNS),
                  integer(body, "seed", (int) StaffService.DEFAULT_SEED),
                  integer(body, "maxPools", OperationParisBaseline.DEFAULT_MAX_POOLS)));
      case "/odds/strafe" ->
          Optional.of(
              service.strafe(
                  game(body),
                  string(body, "attacker"),
                  string(body, "defender"),
                  string(body, "territory"),
                  integer(body, "rounds", 3),
                  integer(body, "landStep", 3),
                  integer(body, "runs", DEFAULT_RUNS)));
      case "/parse-directive" ->
          Optional.of(
              service.parseDirective(
                  game(body), string(body, "directive"), optionalString(body, "model")));
      case "/plan/paris" -> Optional.of(plan(body));
      case "/execute/start" -> Optional.of(executeStart(body));
      case "/execute/orders" -> Optional.of(executeOrders(body));
      case "/execute/status" -> Optional.of(service.execution().status());
      case "/execute/approve" -> Optional.of(executeApprove(body));
      case "/execute/reject" ->
          Optional.of(
              Map.of(
                  "rejected",
                  service
                      .execution()
                      .gate()
                      .reject(optionalString(body, "by").orElse("operator"))));
      case "/execute/report" -> Optional.of(service.execution().report());
      case "/execute/stop" -> Optional.of(executeStop());
      case "/doctrine/list" -> Optional.of(doctrineList(body));
      case "/doctrine/add" -> Optional.of(doctrineAdd(body));
      case "/doctrine/remove" ->
          Optional.of(
              Map.of(
                  "removed",
                  DoctrineStore.open(Path.of(string(body, "file"))).remove(string(body, "id"))));
      case "/doctrine/reconcile" -> Optional.of(doctrineReconcile(body));
      case "/explain" -> Optional.of(explain(body));
      case "/explain-territory" ->
          Optional.of(
              service.explainTerritory(
                  game(body),
                  string(body, "territory"),
                  optionalString(body, "intent").orElse(""),
                  optionalString(body, "model")));
      default -> Optional.empty();
    };
  }

  private Map<String, Object> explain(final JsonObject body) {
    final GameData data = game(body);
    final OperationParisReport report =
        service.paris(
            data,
            integer(body, "runs", DEFAULT_RUNS),
            integer(body, "seed", (int) StaffService.DEFAULT_SEED),
            OperationParisBaseline.DEFAULT_MAX_POOLS);
    return Map.of(
        "report",
        report,
        "briefing",
        service.explain(
            report,
            optionalString(body, "intent").orElse(Cli.DEFAULT_INTENT),
            optionalString(body, "model")));
  }

  private Object plan(final JsonObject body) {
    final PlanReport report =
        service.plan(
            game(body),
            integer(body, "trajectories", DEFAULT_TRAJECTORIES),
            integer(body, "seed", (int) StaffService.DEFAULT_SEED),
            body.has("tolerance") && !body.get("tolerance").isJsonNull()
                ? body.get("tolerance").getAsDouble()
                : 0.0,
            optionalString(body, "intervening").orElse("passive"));
    if (body.has("explain") && body.get("explain").getAsBoolean()) {
      return Map.of(
          "report",
          report,
          "briefing",
          service.explainPlan(
              report,
              optionalString(body, "intent").orElse(Cli.DEFAULT_INTENT),
              optionalString(body, "model")));
    }
    return report;
  }

  private Object executeStart(final JsonObject body) throws IOException {
    final GameData data = game(body);
    final Set<String> staff = new LinkedHashSet<>(strings(body, "staff"));
    if (staff.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(Locale.ROOT, ApiText.MISSING_FIELD, "staff"));
    }
    final String othersName = optionalString(body, "others").orElse("idle");
    final ExecutionSession.Others others =
        switch (othersName) {
          case "idle" -> ExecutionSession.Others.IDLE;
          case "fast" -> ExecutionSession.Others.FAST_AI;
          default -> throw new IllegalArgumentException(CliText.UNKNOWN_OTHERS);
        };
    final String mode = optionalString(body, "mode").orElse("manual");
    final ApprovalGate gate =
        switch (mode) {
          case "manual" ->
              ApprovalGate.manual(Duration.ofMinutes(integer(body, "timeoutMinutes", 60)));
          case "auto-approve" -> ApprovalGate.autoApprove();
          case "auto-reject" -> ApprovalGate.autoReject();
          default -> throw new IllegalArgumentException(CliText.UNKNOWN_MODE);
        };
    final Path work =
        optionalString(body, "work")
            .map(Path::of)
            .orElseGet(
                () ->
                    Path.of(
                        System.getProperty("java.io.tmpdir"),
                        "general-staff-host-" + System.currentTimeMillis()));
    final ExecutionSession session = service.startExecution(data, staff, others, gate, work);
    session.runInBackground(integer(body, "lastRound", 1));
    return session.status();
  }

  private Object executeOrders(final JsonObject body) {
    final ExecutionSession session = service.execution();
    final List<PlannedStep> steps =
        GSON.fromJson(body.get("steps"), new TypeToken<List<PlannedStep>>() {}.getType());
    if (steps == null) {
      throw new IllegalArgumentException(
          String.format(Locale.ROOT, ApiText.MISSING_FIELD, "steps"));
    }
    session.setOrders(steps);
    return session.status();
  }

  private Object executeApprove(final JsonObject body) {
    final ApprovalGate gate = service.execution().gate();
    final String by = optionalString(body, "by").orElse("operator");
    if (body.has("orders") && !body.get("orders").isJsonNull()) {
      final List<PlannedOrder> orders =
          GSON.fromJson(body.get("orders"), new TypeToken<List<PlannedOrder>>() {}.getType());
      return Map.of("approved", gate.approve(orders, by));
    }
    return Map.of("approved", gate.approve(by));
  }

  private Object doctrineList(final JsonObject body) {
    return DoctrineStore.open(Path.of(string(body, "file"))).entries();
  }

  private Object doctrineAdd(final JsonObject body) {
    final DoctrineStore store = DoctrineStore.open(Path.of(string(body, "file")));
    final String directiveText = string(body, "directive");
    final Optional<String> gamePath = optionalString(body, "game");
    GameObservation observation = null;
    StrategicDirective directive = null;
    if (body.has("parsed") && !body.get("parsed").isJsonNull()) {
      directive = IntentParser.parseJson(body.get("parsed").toString());
      if (gamePath.isPresent()) {
        observation = service.observe(service.load(Path.of(gamePath.get())), List.of());
      }
    } else if (gamePath.isPresent()) {
      final GameData data = service.load(Path.of(gamePath.get()));
      final ParsedDirective parsed =
          service.parseDirective(data, directiveText, optionalString(body, "model"));
      directive = parsed.directive();
      observation = service.observe(data, List.of());
    }
    return store.add(
        optionalString(body, "title").orElse(directiveText),
        directiveText,
        directive,
        !body.has("standing") || body.get("standing").getAsBoolean(),
        optionalString(body, "notes").orElse(""),
        observation);
  }

  private Object doctrineReconcile(final JsonObject body) {
    final DoctrineStore store = DoctrineStore.open(Path.of(string(body, "file")));
    return DoctrineReconciler.reconcile(store, service.observe(game(body), List.of()));
  }

  private void serveUi(final HttpExchange exchange, final String path) throws IOException {
    final String name =
        path.equals("/ui") || path.equals("/ui/") ? "index.html" : path.substring("/ui/".length());
    if (name.contains("..") || name.contains("/")) {
      respond(exchange, 404, error(String.format(Locale.ROOT, ApiText.NOT_FOUND, path)), JSON);
      return;
    }
    try (InputStream in =
        LocalHttpApi.class.getResourceAsStream("/org/triplea/generalstaff/ui/" + name)) {
      if (in == null) {
        respond(exchange, 404, error(String.format(Locale.ROOT, ApiText.NOT_FOUND, path)), JSON);
        return;
      }
      final String type =
          name.endsWith(".js")
              ? "text/javascript; charset=utf-8"
              : name.endsWith(".css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8";
      respond(exchange, 200, new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
    }
  }

  private Object executeStop() {
    final Optional<ExecutionSession> session = service.executionIfAny();
    final Object report = session.map(ExecutionSession::report).orElse(null);
    service.stopExecution();
    return report == null ? Map.of("stopped", false) : report;
  }

  private Map<String, Object> health() {
    final List<String> models = service.ollama().models();
    return Map.of(
        "status", "ok", "ollama", !models.isEmpty(), "model", service.model(), "models", models);
  }

  private GameData game(final JsonObject body) {
    return service.load(Path.of(string(body, "game")));
  }

  private static JsonObject readBody(final InputStream in) throws IOException {
    final String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    try {
      final JsonElement element = JsonParser.parseString(text.isBlank() ? "{}" : text);
      if (!element.isJsonObject()) {
        throw new IllegalArgumentException(ApiText.BAD_JSON);
      }
      return element.getAsJsonObject();
    } catch (final JsonSyntaxException e) {
      throw new IllegalArgumentException(ApiText.BAD_JSON, e);
    }
  }

  private static String string(final JsonObject body, final String field) {
    return optionalString(body, field)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format(Locale.ROOT, ApiText.MISSING_FIELD, field)));
  }

  private static Optional<String> optionalString(final JsonObject body, final String field) {
    final JsonElement value = body.get(field);
    return value == null || value.isJsonNull()
        ? Optional.empty()
        : Optional.of(value.getAsString());
  }

  private static int integer(final JsonObject body, final String field, final int defaultValue) {
    final JsonElement value = body.get(field);
    return value == null || value.isJsonNull() ? defaultValue : value.getAsInt();
  }

  private static List<String> strings(final JsonObject body, final String field) {
    final JsonElement value = body.get(field);
    if (value == null || value.isJsonNull()) {
      return List.of();
    }
    final List<String> values = new ArrayList<>();
    if (value.isJsonArray()) {
      for (final JsonElement element : (JsonArray) value) {
        values.add(element.getAsString());
      }
    } else {
      values.add(value.getAsString());
    }
    return values;
  }

  private static String error(final String message) {
    final JsonObject object = new JsonObject();
    object.addProperty("error", message == null ? "unknown error" : message);
    return GSON.toJson(object);
  }

  private static void respond(
      final HttpExchange exchange, final int status, final String body, final String contentType)
      throws IOException {
    final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
