package org.triplea.generalstaff.engine;

import games.strategy.engine.ClientFileSystemHelper;
import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.GameRunner;
import games.strategy.engine.framework.ServerGame;
import games.strategy.engine.framework.startup.ui.PlayerTypes;
import games.strategy.engine.player.Player;
import games.strategy.net.LocalNoOpMessenger;
import games.strategy.net.Messengers;
import games.strategy.net.websocket.ClientNetworkBridge;
import games.strategy.triplea.settings.ClientSetting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A whole TripleA game running in this JVM with no window: the engine's own {@link ServerGame},
 * local players only, headless display and sound, and all files under one work folder. This is the
 * same arrangement the engine's smoke tests use, built from public engine API.
 */
public final class HeadlessGameHost implements AutoCloseable {
  private final ServerGame game;
  private final GameData data;
  private final StaffLaunchAction launchAction;
  private final Path workFolder;
  private volatile boolean stopRequested;
  private volatile Thread engineThread;

  private HeadlessGameHost(
      final ServerGame game,
      final GameData data,
      final StaffLaunchAction launchAction,
      final Path workFolder) {
    this.game = game;
    this.data = data;
    this.launchAction = launchAction;
    this.workFolder = workFolder;
  }

  /**
   * Starts hosting {@code data} with one player implementation per power ({@code playerTypes} is
   * keyed by player name and must cover every power). Saves and scratch files go under {@code
   * workFolder}. The game is ready for {@link #runNextStep()} when this returns.
   */
  public static HeadlessGameHost start(
      final GameData data, final Map<String, PlayerTypes.Type> playerTypes, final Path workFolder)
      throws IOException {
    prepareEnvironment(workFolder);
    final Set<Player> players = data.getGameLoader().newPlayers(playerTypes);
    final StaffLaunchAction launchAction = new StaffLaunchAction();
    final ServerGame game =
        new ServerGame(
            data,
            players,
            new HashMap<>(),
            new Messengers(new LocalNoOpMessenger()),
            ClientNetworkBridge.NO_OP_SENDER,
            launchAction);
    game.setDelegateAutosavesEnabled(false);
    game.setStopGameOnDelegateExecutionStop(true);
    data.getGameLoader().startGame(game, players, launchAction, null);
    launchAction.onLaunch(game);
    game.setUpGameForRunningSteps();
    return new HeadlessGameHost(game, data, launchAction, workFolder);
  }

  /**
   * Points every engine setting a headless game touches at the work folder; writes nothing else.
   */
  static void prepareEnvironment(final Path workFolder) throws IOException {
    EngineSettings.ensureInitialized();
    ClientSetting.aiMovePauseDuration.setValue(0);
    ClientSetting.aiCombatStepPauseDuration.setValue(0);
    final Path saves = workFolder.resolve("saves");
    Files.createDirectories(saves);
    ClientSetting.saveGamesFolderPath.setValue(saves);
    final Path root = workFolder.resolve("root");
    Files.createDirectories(root.resolve("assets"));
    Files.writeString(root.resolve(".triplea-root"), "");
    ClientFileSystemHelper.setCodeSourceFolder(root);
    System.setProperty(GameRunner.TRIPLEA_HEADLESS, "true");
  }

  public ServerGame game() {
    return game;
  }

  public GameData data() {
    return data;
  }

  public Path workFolder() {
    return workFolder;
  }

  public StaffLaunchAction launchAction() {
    return launchAction;
  }

  public String currentStep() {
    return data.getSequence().getStep().getName();
  }

  public int round() {
    return data.getSequence().getRound();
  }

  public boolean isGameOver() {
    return game.isGameOver();
  }

  /** Runs one step on the calling thread; a staff player waiting for approval blocks it. */
  public void runNextStep() {
    game.runNextStep();
  }

  /**
   * Runs steps on the calling thread until the condition holds, the game ends or the cap is hit.
   */
  public int runUntil(final Predicate<GameData> condition, final int maxSteps) {
    int steps = 0;
    while (!condition.test(data) && !game.isGameOver() && !stopRequested && steps < maxSteps) {
      game.runNextStep();
      steps++;
    }
    return steps;
  }

  /** Runs steps on a dedicated engine thread until the condition holds or the host is closed. */
  public Thread runInBackground(final Predicate<GameData> stopWhen) {
    final Thread thread =
        new Thread(
            () -> {
              while (!stopRequested && !game.isGameOver() && !stopWhen.test(data)) {
                game.runNextStep();
              }
            },
            "general-staff-engine");
    thread.setDaemon(true);
    engineThread = thread;
    thread.start();
    return thread;
  }

  public boolean isRunning() {
    final Thread thread = engineThread;
    return thread != null && thread.isAlive();
  }

  /** Writes a full save game (with delegates and history) to {@code file}. */
  public void save(final Path file) {
    game.saveGame(file);
  }

  @Override
  public void close() {
    stopRequested = true;
    if (!game.isGameOver()) {
      game.stopGame();
    }
    final Thread thread = engineThread;
    if (thread != null) {
      try {
        thread.join(10_000);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
