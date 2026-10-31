package org.triplea.generalstaff.sim;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.plan.ForceSelection;
import org.triplea.generalstaff.sim.TurnSimulator.BattleOutcome;
import org.triplea.generalstaff.sim.TurnSimulator.Order;
import org.triplea.generalstaff.sim.TurnSimulator.StepRun;

class TurnSimulatorTest {
  private static byte[] start;

  @BeforeAll
  static void snapshot() {
    final Sandbox base = Sandbox.fromLive(TestMapGameData.GLOBAL1940.getGameData(), 1L);
    new TurnSimulator(base).initializeIfFresh();
    start = base.snapshot();
  }

  @Test
  void refusedOrdersAreReportedAndAcceptedOnesMoveUnits() {
    final Sandbox sandbox = Sandbox.fromBytes(start, 3L);
    final TurnSimulator simulator = new TurnSimulator(sandbox);
    final GamePlayer germans = sandbox.player("Germans");
    final Territory holland = sandbox.territory("Holland Belgium");
    final Territory germany = sandbox.territory("Germany");
    final Territory westernGermany = sandbox.territory("Western Germany");
    final Territory france = sandbox.territory("France");
    final List<Unit> twoInfantry = units(holland, germans, "infantry", 2);
    final List<Unit> farInfantry = units(germany, germans, "infantry", 1);

    final StepRun run =
        simulator.runStep(
            "germansCombatMove",
            List.of(
                new Order(twoInfantry, new Route(holland, france)),
                new Order(farInfantry, new Route(germany, westernGermany, france))));

    assertThat(run.step()).isEqualTo("germansCombatMove");
    assertThat(run.player()).isEqualTo("Germans");
    assertThat(run.ordersAccepted()).isEqualTo(1);
    assertThat(run.orderErrors()).hasSize(1);
    assertThat(france.getUnits()).containsAll(twoInfantry);
    assertThat(germany.getUnits()).containsAll(farInfantry);
    assertThat(simulator.isCombatMove()).isTrue();
  }

  @Test
  void theBattleStepFightsWithTheRealEngineAndRecordsTheOutcome() {
    final Sandbox sandbox = Sandbox.fromBytes(start, 5L);
    final TurnSimulator simulator = new TurnSimulator(sandbox);
    final GamePlayer germans = sandbox.player("Germans");
    final Territory france = sandbox.territory("France");
    final List<Unit> force = ForceSelection.strikeForce(sandbox.data(), germans, france);
    final List<Order> orders =
        List.of(
            new Order(
                units(sandbox.territory("Holland Belgium"), germans, "infantry", 4),
                new Route(sandbox.territory("Holland Belgium"), france)),
            new Order(
                units(sandbox.territory("Holland Belgium"), germans, "armour", 3),
                new Route(sandbox.territory("Holland Belgium"), france)),
            new Order(
                units(sandbox.territory("Western Germany"), germans, "fighter", 2),
                new Route(sandbox.territory("Western Germany"), france)),
            new Order(
                units(sandbox.territory("Western Germany"), germans, "tactical_bomber", 3),
                new Route(sandbox.territory("Western Germany"), france)));
    assertThat(simulator.runStep("germansCombatMove", orders).orderErrors()).isEmpty();

    final StepRun battle = simulator.runStep("germansBattle");

    assertThat(battle.battles()).hasSize(1);
    final BattleOutcome outcome = battle.battles().get(0);
    assertThat(outcome.territory()).isEqualTo("France");
    assertThat(outcome.attacker()).isEqualTo("Germans");
    assertThat(outcome.defender()).isEqualTo("French");
    assertThat(outcome.rounds()).isGreaterThanOrEqualTo(1);
    assertThat(outcome.result()).isIn("ATTACKER", "DEFENDER", "DRAW");
    if (outcome.result().equals("ATTACKER")) {
      assertThat(outcome.ownerAfter()).isEqualTo("Germans");
      assertThat(outcome.defendersLeft()).isEmpty();
    } else {
      assertThat(outcome.ownerAfter())
          .isEqualTo("France".equals(outcome.territory()) ? "French" : outcome.ownerAfter());
    }
    assertThat(force).isNotEmpty();
  }

  @Test
  void theRetreatPolicyStopsABattleAfterTheGivenRound() {
    final Sandbox sandbox = Sandbox.fromBytes(start, 11L);
    final TurnSimulator simulator = new TurnSimulator(sandbox);
    final GamePlayer germans = sandbox.player("Germans");
    final Territory france = sandbox.territory("France");
    final Territory holland = sandbox.territory("Holland Belgium");
    sandbox.policy("Germans").retreatAfterRound(1);
    assertThat(
            simulator
                .runStep(
                    "germansCombatMove",
                    List.of(
                        new Order(
                            units(holland, germans, "infantry", 2), new Route(holland, france))))
                .orderErrors())
        .isEmpty();

    final BattleOutcome outcome = simulator.runStep("germansBattle").battles().get(0);

    assertThat(outcome.rounds()).isEqualTo(1);
    assertThat(outcome.ownerAfter()).isEqualTo("French");
    assertThat(sandbox.policy("Germans").retreatRound()).isEqualTo(1);
  }

  private static List<Unit> units(
      final Territory territory, final GamePlayer owner, final String type, final int count) {
    final List<Unit> units =
        territory
            .getMatches(
                Matches.unitIsOwnedBy(owner).and(unit -> unit.getType().getName().equals(type)))
            .stream()
            .limit(count)
            .toList();
    assertThat(units).as("%d %s in %s", count, type, territory.getName()).hasSize(count);
    return units;
  }

  @Test
  void anAirOnlyStrikeRetreatsAfterTheGivenRoundToo() {
    // A lone battle is fought by the battle tracker itself, bypassing the delegate's "current
    // battle"; the retreat policy must still see the round, through the battle id the engine
    // passes.
    final Sandbox sandbox = Sandbox.fromBytes(start, 13L);
    final TurnSimulator simulator = new TurnSimulator(sandbox);
    final GamePlayer germans = sandbox.player("Germans");
    final Territory france = sandbox.territory("France");
    sandbox.policy("Germans").retreatAfterRound(1);
    final List<Unit> aircraft = ForceSelection.airStrikeForce(sandbox.data(), germans, france);
    assertThat(aircraft).hasSize(9);
    final List<Order> orders = new ArrayList<>();
    for (final Territory origin : sandbox.data().getMap().getTerritories()) {
      final List<Unit> here = origin.getUnits().stream().filter(aircraft::contains).toList();
      if (!here.isEmpty()) {
        orders.add(
            new Order(
                here,
                sandbox
                    .data()
                    .getMap()
                    .getRouteForUnits(
                        origin, france, Matches.territoryIsNotImpassable(), here, germans)
                    .orElseThrow()));
      }
    }
    assertThat(simulator.runStep("germansCombatMove", orders).orderErrors()).isEmpty();

    final BattleOutcome outcome = simulator.runStep("germansBattle").battles().get(0);

    assertThat(outcome.rounds()).isEqualTo(1);
    assertThat(outcome.ownerAfter()).isEqualTo("French");
    assertThat(outcome.defendersLeft()).isNotEmpty();
    // An all-air attacker retreats in place: the engine leaves the survivors over the battle site,
    // where they cannot stay, so the non-combat move must fly them home.
    final List<Unit> overFrance = france.getMatches(Matches.unitIsOwnedBy(germans));
    assertThat(overFrance).isNotEmpty().allMatch(Matches.unitIsAir());
    sandbox.position("germansNonCombatMove");
    final List<Order> landing = LandingPolicy.orders(sandbox.data(), germans, sandbox.judge());
    assertThat(landing).hasSize(overFrance.size());
    assertThat(simulator.runStep("germansNonCombatMove", landing).orderErrors()).isEmpty();
    assertThat(france.getMatches(Matches.unitIsOwnedBy(germans))).isEmpty();
    final long survivors =
        sandbox.data().getMap().getTerritories().stream()
            .filter(territory -> !territory.isWater())
            .flatMap(territory -> territory.getUnits().stream())
            .filter(unit -> overFrance.stream().anyMatch(a -> a.getId().equals(unit.getId())))
            .count();
    assertThat(survivors).isEqualTo(overFrance.size());
  }
}
