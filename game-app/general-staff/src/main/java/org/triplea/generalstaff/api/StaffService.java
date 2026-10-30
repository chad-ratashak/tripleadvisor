package org.triplea.generalstaff.api;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.util.TuvCostsCalculator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.triplea.generalstaff.engine.GameLoading;
import org.triplea.generalstaff.llm.Briefing;
import org.triplea.generalstaff.llm.Explainer;
import org.triplea.generalstaff.llm.IntentParser;
import org.triplea.generalstaff.llm.OllamaClient;
import org.triplea.generalstaff.llm.ParsedDirective;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.HistoryLog;
import org.triplea.generalstaff.observe.HistoryLogExporter;
import org.triplea.generalstaff.observe.ObservationExporter;
import org.triplea.generalstaff.odds.BattleOddsService;
import org.triplea.generalstaff.odds.ForceSubsets;
import org.triplea.generalstaff.odds.StrafeEvaluator;
import org.triplea.generalstaff.odds.StrafeSweep;
import org.triplea.generalstaff.plan.ForceSelection;
import org.triplea.generalstaff.plan.OperationParisBaseline;
import org.triplea.generalstaff.plan.OperationParisPlanner;
import org.triplea.generalstaff.plan.OperationParisReport;
import org.triplea.generalstaff.plan.PlanReport;
import org.triplea.generalstaff.rules.LegalityChecker;
import org.triplea.generalstaff.rules.MoveVerdict;
import org.triplea.generalstaff.rules.ReachabilityIndex;
import org.triplea.generalstaff.rules.ReachabilityReport;
import org.triplea.generalstaff.sim.OpponentPolicy;
import org.triplea.generalstaff.staff.ApprovalGate;
import org.triplea.generalstaff.staff.ExecutionSession;

/**
 * The General Staff's services behind one front door, shared by the command line and the local HTTP
 * API. Games are loaded once per file and reloaded when the file changes. Everything here is
 * read-only with respect to the loaded game.
 */
public final class StaffService {
  public static final long DEFAULT_SEED = 1L;

  private record CachedGame(GameData data, FileTime modified) {}

  private final Map<Path, CachedGame> games = new ConcurrentHashMap<>();
  private final OllamaClient ollama;
  private final AtomicReference<ExecutionSession> execution = new AtomicReference<>();
  private final String model;
  private final ObservationExporter observations = new ObservationExporter();
  private final HistoryLogExporter histories = new HistoryLogExporter();

  public StaffService(final OllamaClient ollama, final String model) {
    this.ollama = ollama;
    this.model = model;
  }

  public OllamaClient ollama() {
    return ollama;
  }

  public String model() {
    return model;
  }

  /** The game in {@code file}, loaded on first use and again whenever the file changes. */
  public GameData load(final Path file) {
    final Path key = file.toAbsolutePath().normalize();
    final FileTime modified = lastModified(key);
    final CachedGame cached = games.get(key);
    if (cached != null && cached.modified().equals(modified)) {
      return cached.data();
    }
    final GameData data = GameLoading.load(key);
    games.put(key, new CachedGame(data, modified));
    return data;
  }

  public GameObservation observe(final GameData data, final List<String> focus) {
    return observations.export(data, focus);
  }

  public String toJson(final GameObservation observation) {
    return observations.toJson(observation);
  }

  public HistoryLog history(final GameData data) {
    return histories.export(data);
  }

  public String toJson(final HistoryLog history) {
    return histories.toJson(history);
  }

  public MoveVerdict legal(
      final GameData data,
      final String step,
      final String from,
      final String to,
      final String type,
      final int count) {
    final LegalityChecker checker = LegalityChecker.forStep(data, step, DEFAULT_SEED);
    final Territory origin = territory(data, from);
    final GamePlayer player = player(data, checker.player().getName());
    final List<Unit> units =
        origin
            .getMatches(
                Matches.unitIsOwnedBy(player).and(unit -> unit.getType().getName().equals(type)))
            .stream()
            .limit(count)
            .toList();
    if (units.size() < count) {
      throw new IllegalArgumentException(
          String.format(Locale.ROOT, CliText.NO_SUCH_UNITS, count, type, origin.getName()));
    }
    return checker.check(units, origin, territory(data, to));
  }

  public ReachabilityReport reach(
      final GameData data, final String step, final Optional<String> from) {
    from.ifPresent(name -> territory(data, name));
    return ReachabilityIndex.compute(LegalityChecker.forStep(data, step, DEFAULT_SEED), from);
  }

  public OperationParisReport paris(
      final GameData data, final int runs, final long seed, final int maxPools) {
    return new OperationParisBaseline(
            data, "Germans", "Italians", "French", "France", seed, maxPools)
        .run(runs);
  }

  public StrafeSweep strafe(
      final GameData data,
      final String attackerName,
      final String defenderName,
      final String territoryName,
      final int rounds,
      final int landStep,
      final int runs) {
    final GamePlayer attacker = player(data, attackerName);
    final GamePlayer defender = player(data, defenderName);
    final Territory territory = territory(data, territoryName);
    final List<ForceSubsets.Force> forces =
        ForceSubsets.byLandCount(
            ForceSelection.strikeForce(data, attacker, territory),
            landStep,
            new TuvCostsCalculator().getCostsForTuv(attacker));
    try (BattleOddsService odds = new BattleOddsService(data)) {
      return new StrafeEvaluator(odds)
          .sweep(
              attacker,
              defender,
              territory,
              forces,
              List.copyOf(territory.getUnits()),
              rounds,
              runs);
    }
  }

  public PlanReport plan(
      final GameData data,
      final int trajectories,
      final long seed,
      final double violationTolerance,
      final String interveningPolicy) {
    final OperationParisPlanner.Settings settings =
        OperationParisPlanner.Settings.defaults()
            .withTrajectories(trajectories)
            .withSeed(seed)
            .withViolationTolerance(violationTolerance)
            .withInterveningPolicy(
                "reinforce".equals(interveningPolicy)
                    ? OpponentPolicy.reinforce("France")
                    : OpponentPolicy.passive());
    return OperationParisPlanner.forGlobal1940(data, settings).plan();
  }

  public Briefing explainPlan(
      final PlanReport report, final String intent, final Optional<String> modelOverride) {
    return new Explainer(ollama, modelOverride.orElse(model))
        .brief(intent, Explainer.facts(report));
  }

  /** Starts hosting a game with the staff as the named powers; any earlier session is closed. */
  public ExecutionSession startExecution(
      final GameData data,
      final Set<String> staffPlayers,
      final ExecutionSession.Others others,
      final ApprovalGate gate,
      final Path workFolder)
      throws IOException {
    stopExecution();
    final ExecutionSession session =
        ExecutionSession.start(data, staffPlayers, others, gate, workFolder);
    execution.set(session);
    return session;
  }

  public ExecutionSession execution() {
    final ExecutionSession session = execution.get();
    if (session == null) {
      throw new IllegalStateException(CliText.NO_EXECUTION);
    }
    return session;
  }

  public Optional<ExecutionSession> executionIfAny() {
    return Optional.ofNullable(execution.get());
  }

  public void stopExecution() {
    final ExecutionSession session = execution.getAndSet(null);
    if (session != null) {
      session.close();
    }
  }

  public ParsedDirective parseDirective(
      final GameData data, final String directive, final Optional<String> modelOverride) {
    return new IntentParser(ollama, modelOverride.orElse(model))
        .parse(directive, observations.export(data));
  }

  public Briefing explain(
      final OperationParisReport report,
      final String intent,
      final Optional<String> modelOverride) {
    return new Explainer(ollama, modelOverride.orElse(model))
        .brief(intent, Explainer.facts(report));
  }

  public Briefing explainTerritory(
      final GameData data,
      final String territoryName,
      final String intent,
      final Optional<String> modelOverride) {
    final GameObservation observation = observations.export(data, List.of(territoryName));
    return new Explainer(ollama, modelOverride.orElse(model))
        .brief(intent, Explainer.facts(observation, territoryName));
  }

  static Territory territory(final GameData data, final String name) {
    final Territory territory = data.getMap().getTerritoryOrNull(name);
    if (territory == null) {
      throw new IllegalArgumentException(
          String.format(Locale.ROOT, CliText.UNKNOWN_TERRITORY, name));
    }
    return territory;
  }

  static GamePlayer player(final GameData data, final String name) {
    final GamePlayer player = data.getPlayerList().getPlayerId(name);
    if (player == null) {
      throw new IllegalArgumentException(String.format(Locale.ROOT, CliText.UNKNOWN_PLAYER, name));
    }
    return player;
  }

  private static FileTime lastModified(final Path file) {
    try {
      return Files.getLastModifiedTime(file);
    } catch (final IOException e) {
      throw new IllegalArgumentException("Game file not found: " + file, e);
    }
  }
}
