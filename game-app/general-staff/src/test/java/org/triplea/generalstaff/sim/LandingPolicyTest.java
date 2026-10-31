package org.triplea.generalstaff.sim;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.sim.TurnSimulator.Order;

/**
 * Aircraft that end the combat move over an empty sea zone cannot stay there. Without landing
 * orders the engine removes them at the end of the non-combat move; with the policy's orders they
 * come home to German land.
 */
class LandingPolicyTest {
  @Test
  void aircraftLeftOverAnEmptySeaZoneAreOrderedHomeAndSurvive() {
    final Sandbox base = Sandbox.fromLive(TestMapGameData.GLOBAL1940.getGameData(), 1L);
    new TurnSimulator(base).initializeIfFresh();
    final Sandbox sandbox = base.fork(2L);
    final TurnSimulator simulator = new TurnSimulator(sandbox);
    final GamePlayer germans = sandbox.player("Germans");
    final Territory westernGermany = sandbox.territory("Western Germany");
    final Territory seaZone = sandbox.territory("112 Sea Zone");
    final List<Unit> fighters =
        westernGermany.getMatches(
            Matches.unitIsOwnedBy(germans).and(unit -> unit.getType().getName().equals("fighter")));
    assertThat(fighters).hasSize(2);
    assertThat(seaZone.getUnits()).isEmpty();

    assertThat(
            simulator
                .runStep(
                    "germansCombatMove",
                    List.of(new Order(fighters, new Route(westernGermany, seaZone))))
                .orderErrors())
        .isEmpty();
    assertThat(simulator.runStep("germansBattle").battles()).isEmpty();
    assertThat(seaZone.getUnits()).containsExactlyInAnyOrderElementsOf(fighters);

    // Control: with no landing orders the engine removes both fighters when the step ends.
    final Sandbox control = sandbox.fork(3L);
    new TurnSimulator(control).runStep("germansNonCombatMove");
    for (final Unit fighter : fighters) {
      assertThat(holder(control, fighter)).isEmpty();
    }

    sandbox.position("germansNonCombatMove");
    final List<Order> landing = LandingPolicy.orders(sandbox.data(), germans, sandbox.judge());
    assertThat(landing).hasSize(2);
    assertThat(landing)
        .allSatisfy(
            order -> {
              assertThat(order.route().getStart()).isEqualTo(seaZone);
              assertThat(order.route().getEnd().isWater()).isFalse();
              assertThat(order.route().getEnd().getOwner()).isEqualTo(germans);
            });
    assertThat(simulator.runStep("germansNonCombatMove", landing).orderErrors()).isEmpty();
    for (final Unit fighter : fighters) {
      final Territory home = holder(sandbox, fighter).orElseThrow();
      assertThat(home.isWater()).isFalse();
      assertThat(home.getOwner()).isEqualTo(germans);
    }
  }

  private static Optional<Territory> holder(final Sandbox sandbox, final Unit unit) {
    return sandbox.data().getMap().getTerritories().stream()
        .filter(
            territory ->
                territory.getUnits().stream().anyMatch(u -> u.getId().equals(unit.getId())))
        .findFirst();
  }
}
