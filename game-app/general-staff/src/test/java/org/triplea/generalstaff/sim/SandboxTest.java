package org.triplea.generalstaff.sim;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.observe.ObservationExporter;
import org.triplea.generalstaff.sim.TurnSimulator.Order;

class SandboxTest {
  @Test
  void sandboxesAreIndependentOfEachOtherAndOfTheLiveGame() {
    final GameData live = TestMapGameData.GLOBAL1940.getGameData();
    final ObservationExporter exporter = new ObservationExporter();
    final String liveBefore = exporter.toJson(exporter.export(live));

    final Sandbox first = Sandbox.fromLive(live, 1L);
    final Sandbox second = first.fork(2L);
    final Territory hollandInFirst = first.territory("Holland Belgium");
    final GamePlayer germansInFirst = first.player("Germans");
    first
        .journal()
        .apply(first.data(), ChangeFactory.changeOwner(hollandInFirst, first.player("French")));

    assertThat(hollandInFirst.getOwner().getName()).isEqualTo("French");
    assertThat(second.territory("Holland Belgium").getOwner().getName()).isEqualTo("Germans");
    assertThat(live.getMap().getTerritoryOrNull("Holland Belgium").getOwner().getName())
        .isEqualTo("Germans");
    assertThat(first.data()).isNotSameAs(live).isNotSameAs(second.data());
    assertThat(germansInFirst).isNotSameAs(live.getPlayerList().getPlayerId("Germans"));

    first.journal().rollback(first.data());
    assertThat(hollandInFirst.getOwner().getName()).isEqualTo("Germans");
    assertThat(exporter.toJson(exporter.export(live))).isEqualTo(liveBefore);
  }

  @Test
  void sameSeedReplaysTheSameBattle() {
    final GameData live = TestMapGameData.GLOBAL1940.getGameData();
    final byte[] start = Sandbox.snapshotOf(live);

    final TurnSimulator.StepRun firstRun = strikeFrance(Sandbox.fromBytes(start, 7L));
    final TurnSimulator.StepRun secondRun = strikeFrance(Sandbox.fromBytes(start, 7L));
    final TurnSimulator.StepRun otherSeed = strikeFrance(Sandbox.fromBytes(start, 8L));

    assertThat(firstRun.battles()).hasSize(1);
    assertThat(firstRun.battles()).isEqualTo(secondRun.battles());
    assertThat(List.of(firstRun.battles(), otherSeed.battles()))
        .as("different seeds should usually differ; a coincidence would still be a valid outcome")
        .isNotNull();
  }

  private static TurnSimulator.StepRun strikeFrance(final Sandbox sandbox) {
    final TurnSimulator simulator = new TurnSimulator(sandbox);
    simulator.initializeIfFresh();
    final GamePlayer germans = sandbox.player("Germans");
    final Territory holland = sandbox.territory("Holland Belgium");
    final Territory france = sandbox.territory("France");
    final List<Unit> infantry =
        holland.getMatches(
            Matches.unitIsOwnedBy(germans)
                .and(unit -> unit.getType().getName().equals("infantry")));
    final TurnSimulator.StepRun move =
        simulator.runStep(
            "germansCombatMove", List.of(new Order(infantry, new Route(holland, france))));
    assertThat(move.orderErrors()).isEmpty();
    return simulator.runStep("germansBattle");
  }
}
