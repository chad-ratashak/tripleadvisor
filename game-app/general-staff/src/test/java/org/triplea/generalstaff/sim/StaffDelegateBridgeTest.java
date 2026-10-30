package org.triplea.generalstaff.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.random.IRandomStats.DiceType;
import games.strategy.triplea.xml.TestMapGameData;
import org.junit.jupiter.api.Test;

class StaffDelegateBridgeTest {
  @Test
  void seededDiceReplayExactly() {
    final GameData data = TestMapGameData.MINIMAP.getGameData();
    final GamePlayer russians = data.getPlayerList().getPlayerId("Russians");

    final StaffDelegateBridge first = StaffDelegateBridge.forSimulation(data, russians, 42L);
    final StaffDelegateBridge second = StaffDelegateBridge.forSimulation(data, russians, 42L);
    final StaffDelegateBridge other = StaffDelegateBridge.forSimulation(data, russians, 43L);

    final int[] firstRolls = first.getRandom(6, 50, russians, DiceType.COMBAT, "test");
    assertThat(firstRolls).isEqualTo(second.getRandom(6, 50, russians, DiceType.COMBAT, "test"));
    assertThat(firstRolls).isNotEqualTo(other.getRandom(6, 50, russians, DiceType.COMBAT, "test"));
    assertThat(java.util.Arrays.stream(firstRolls).allMatch(roll -> roll >= 0 && roll < 6))
        .isTrue();
  }

  @Test
  void rollbackUndoesRecordedChanges() {
    final GameData data = TestMapGameData.MINIMAP.getGameData();
    final GamePlayer russians = data.getPlayerList().getPlayerId("Russians");
    final GamePlayer italians = data.getPlayerList().getPlayerId("Italians");
    final Territory blueCapitol = data.getMap().getTerritoryOrNull("blue capitol");
    assertThat(blueCapitol.getOwner()).isEqualTo(italians);

    final StaffDelegateBridge bridge = StaffDelegateBridge.forSimulation(data, russians, 1L);
    bridge.addChange(ChangeFactory.changeOwner(blueCapitol, russians));
    assertThat(blueCapitol.getOwner()).isEqualTo(russians);
    assertThat(bridge.changeCount()).isEqualTo(1);

    bridge.rollback();
    assertThat(blueCapitol.getOwner()).isEqualTo(italians);
    assertThat(bridge.changeCount()).isZero();
  }

  @Test
  void readOnlyBridgeRefusesChanges() {
    final GameData data = TestMapGameData.MINIMAP.getGameData();
    final GamePlayer russians = data.getPlayerList().getPlayerId("Russians");
    final Territory blueCapitol = data.getMap().getTerritoryOrNull("blue capitol");

    final StaffDelegateBridge bridge = StaffDelegateBridge.readOnly(data, russians);

    assertThatThrownBy(() -> bridge.addChange(ChangeFactory.changeOwner(blueCapitol, russians)))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(bridge.getData()).isSameAs(data);
    assertThat(bridge.getGamePlayer()).isEqualTo(russians);
    assertThat(bridge.getRemotePlayer()).isInstanceOf(SimulationPlayer.class);
  }
}
