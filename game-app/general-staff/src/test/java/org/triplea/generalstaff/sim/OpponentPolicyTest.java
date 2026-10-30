package org.triplea.generalstaff.sim;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.sim.TurnSimulator.Order;
import org.triplea.generalstaff.sim.TurnSimulator.StepRun;

class OpponentPolicyTest {
  @Test
  void passivePolicyIssuesNothing() {
    final Sandbox sandbox = Sandbox.fromLive(TestMapGameData.GLOBAL1940.getGameData(), 1L);
    sandbox.position("britishNonCombatMove");

    assertThat(
            OpponentPolicy.passive()
                .orders(sandbox, sandbox.player("British"), "britishNonCombatMove"))
        .isEmpty();
    assertThat(OpponentPolicy.passive().name()).isEqualTo("passive");
  }

  @Test
  void reinforcePolicyOnlyIssuesOrdersTheEngineAccepts() {
    final Sandbox sandbox = Sandbox.fromLive(TestMapGameData.GLOBAL1940.getGameData(), 1L);
    final TurnSimulator simulator = new TurnSimulator(sandbox);
    simulator.initializeIfFresh();
    final GamePlayer british = sandbox.player("British");
    final Territory france = sandbox.territory("France");
    final int unitsBefore = france.getUnits().size();
    sandbox.position("britishNonCombatMove");

    final OpponentPolicy policy = OpponentPolicy.reinforce("France");
    final List<Order> orders = policy.orders(sandbox, british, "britishNonCombatMove");
    final StepRun run = simulator.runStep("britishNonCombatMove", orders);

    assertThat(policy.name()).contains("France");
    assertThat(run.orderErrors()).isEmpty();
    assertThat(run.ordersAccepted()).isEqualTo(orders.size());
    assertThat(france.getUnits().size()).isGreaterThanOrEqualTo(unitsBefore);
    System.out.println(
        "British units that can reinforce France on turn 1: "
            + orders.size()
            + " orders, France now holds "
            + france.getUnits().size()
            + " units");
  }
}
