package org.triplea.generalstaff.engine;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.startup.ui.PlayerTypes;
import games.strategy.engine.player.Player;
import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triplea.generalstaff.sim.SimulationPlayer;

class HeadlessGameHostTest {
  @Test
  void hostsAWholeGameInProcessAndCanSaveIt(@TempDir final Path work) throws Exception {
    final GameData data = TestMapGameData.MINIMAP.getGameData();
    final Map<String, PlayerTypes.Type> types = new HashMap<>();
    data.getPlayerList()
        .getPlayers()
        .forEach(
            player ->
                types.put(
                    player.getName(),
                    new PlayerTypes.Type(SimulationPlayer.LABEL) {
                      @Override
                      public Player newPlayerWithName(final String name) {
                        return new SimulationPlayer(name);
                      }
                    }));

    try (HeadlessGameHost host = HeadlessGameHost.start(data, types, work)) {
      final int steps = host.runUntil(game -> game.getSequence().getRound() >= 2, 200);
      assertThat(steps).isGreaterThan(5);
      assertThat(host.round()).isEqualTo(2);
      assertThat(host.isGameOver()).isFalse();
      final Path save = work.resolve("round2.tsvg");
      host.save(save);
      assertThat(Files.size(save)).isGreaterThan(1000);
      assertThat(GameLoading.load(save).getSequence().getRound()).isEqualTo(2);
      assertThat(host.launchAction().messages()).isEmpty();
    }
  }
}
