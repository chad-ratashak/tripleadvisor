package org.triplea.generalstaff.engine;

import games.strategy.engine.chat.Chat;
import games.strategy.engine.framework.AutoSaveFileUtils;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.framework.LocalPlayers;
import games.strategy.engine.framework.ServerGame;
import games.strategy.engine.framework.startup.WatcherThreadMessaging;
import games.strategy.engine.framework.startup.launcher.LaunchAction;
import games.strategy.engine.framework.startup.mc.IServerStartupRemote;
import games.strategy.engine.framework.startup.mc.ServerConnectionProps;
import games.strategy.engine.framework.startup.mc.ServerModel;
import games.strategy.engine.framework.startup.ui.PlayerTypes;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.engine.player.Player;
import games.strategy.net.Messengers;
import games.strategy.net.websocket.ClientNetworkBridge;
import games.strategy.triplea.ResourceLoader;
import games.strategy.triplea.ui.display.HeadlessDisplay;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import org.triplea.game.chat.ChatModel;
import org.triplea.sound.HeadlessSoundChannel;

/**
 * How a game hosted by the General Staff presents itself to the engine: no display, no sound, no
 * map images, no lobby. The two methods the lobby startup would call are not supported, because an
 * in-process host never reaches them.
 */
public final class StaffLaunchAction implements LaunchAction {
  private final List<String> messages = new CopyOnWriteArrayList<>();
  @Nullable private volatile ServerGame serverGame;

  /** Messages the engine reported through {@link #onEnd} or {@link #handleError}. */
  public List<String> messages() {
    return List.copyOf(messages);
  }

  public Optional<ServerGame> serverGame() {
    return Optional.ofNullable(serverGame);
  }

  @Override
  public void handleGameInterruption(
      final GameSelectorModel gameSelectorModel, final ServerModel serverModel) {
    // nothing to resume: the host owns the game's lifetime
  }

  @Override
  public void onGameInterrupt() {
    // no lobby to return to
  }

  @Override
  public void onEnd(final String message) {
    messages.add(message);
  }

  @Override
  public Collection<PlayerTypes.Type> getPlayerTypes() {
    return PlayerTypes.getBuiltInPlayerTypes();
  }

  @Override
  public void startGame(
      final LocalPlayers localPlayers,
      final IGame game,
      final Set<Player> players,
      final Chat chat) {
    game.setResourceLoader(new ResourceLoader(List.of()));
    game.setDisplay(new HeadlessDisplay());
    game.setSoundChannel(new HeadlessSoundChannel());
  }

  @Override
  public Path getAutoSaveFile() {
    return getAutoSaveFileUtils().getOddRoundAutoSaveFile();
  }

  @Override
  public void onLaunch(final ServerGame game) {
    serverGame = game;
  }

  @Override
  public AutoSaveFileUtils getAutoSaveFileUtils() {
    return new AutoSaveFileUtils();
  }

  @Override
  public ChatModel createChatModel(
      final String chatName,
      final Messengers messengers,
      final ClientNetworkBridge clientNetworkBridge) {
    throw new UnsupportedOperationException("A General Staff host has no chat");
  }

  @Override
  public boolean shouldMinimizeExpensiveAiUse() {
    return true;
  }

  @Override
  public WatcherThreadMessaging createThreadMessaging() {
    return new WatcherThreadMessaging.HeadlessWatcherThreadMessaging();
  }

  @Override
  public Optional<ServerConnectionProps> getFallbackConnection(final Runnable cancelAction) {
    return Optional.empty();
  }

  @Override
  public void handleError(final String error) {
    messages.add(error);
  }

  @Override
  public IServerStartupRemote getStartupRemote(
      final IServerStartupRemote.ServerModelView serverModelView) {
    throw new UnsupportedOperationException("A General Staff host has no lobby startup");
  }

  @Override
  public boolean promptGameStop(
      final String status, final String title, @Nullable final Path mapLocation) {
    return true;
  }

  @Override
  public PlayerTypes.Type getDefaultLocalPlayerType() {
    return PlayerTypes.WEAK_AI;
  }
}
