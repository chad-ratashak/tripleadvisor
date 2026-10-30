package org.triplea.generalstaff.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import games.strategy.engine.data.GameData;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.observe.ObservationExporter;
import org.triplea.generalstaff.plan.OperationParisPlanner.Family;
import org.triplea.generalstaff.plan.OperationParisPlanner.Settings;
import org.triplea.generalstaff.plan.PlanReport.OperationalPlan;
import org.triplea.generalstaff.sim.SimulationPlayer;

/**
 * Constraint handling and ranking on a small sample: the full attack must be rejected, the air-only
 * family must never conquer, and the live game must be untouched.
 */
class OperationParisPlannerTest {
  private static final int TRAJECTORIES = Integer.getInteger("generalstaff.trajectories", 6);

  @Test
  void ranksFeasiblePlansFirstAndRejectsTheFullAttack() {
    final GameData live = TestMapGameData.GLOBAL1940.getGameData();
    final ObservationExporter exporter = new ObservationExporter();
    final String before = exporter.toJson(exporter.export(live));
    final Settings settings =
        Settings.defaults()
            .withTrajectories(TRAJECTORIES)
            .withSeed(3L)
            .withFamilies(
                List.of(
                    new Family(
                        "italy-alone",
                        "Italy alone",
                        Family.NO_STRIKE,
                        SimulationPlayer.NEVER,
                        true),
                    new Family("air-strike-r2", "air only, retreat after round 2", 0, 2, true),
                    new Family(
                        "full-attack",
                        "everything to the end",
                        Family.ALL_LAND,
                        SimulationPlayer.NEVER,
                        true)));

    final long started = System.nanoTime();
    final PlanReport report = OperationParisPlanner.forGlobal1940(live, settings).plan();
    System.out.println(PlanFormatter.toText(report));
    System.out.println(
        "Planner wall time: "
            + (System.nanoTime() - started) / 1_000_000
            + " ms for "
            + (3 * TRAJECTORIES)
            + " trajectories");

    assertThat(report.plans()).hasSize(3);
    assertThat(report.plans()).extracting(OperationalPlan::rank).containsExactly(1, 2, 3);
    final OperationalPlan fullAttack = plan(report, "full-attack");
    final OperationalPlan airStrike = plan(report, "air-strike-r2");
    final OperationalPlan italyAlone = plan(report, "italy-alone");
    assertThat(fullAttack.metrics().germanyConquersFrance().rate()).isGreaterThan(0.5);
    assertThat(fullAttack.feasible()).isFalse();
    assertThat(fullAttack.constraintViolations()).isNotEmpty();
    assertThat(airStrike.metrics().germanyConquersFrance().successes()).isZero();
    assertThat(airStrike.metrics().allOrdersAccepted().rate()).isEqualTo(1.0);
    assertThat(airStrike.feasible()).isTrue();
    assertThat(italyAlone.metrics().germanyConquersFrance().successes()).isZero();
    assertThat(report.plans().get(0).feasible()).isTrue();
    assertThat(report.plans().get(2).id()).isEqualTo("full-attack");
    assertThat(airStrike.orders()).isNotEmpty();
    assertThat(airStrike.orders().get(0).step()).isEqualTo("germansCombatMove");
    assertThat(airStrike.orders().get(0).orders())
        .allMatch(order -> order.route().get(order.route().size() - 1).equals("France"));
    assertThat(report.notEvaluated())
        .extracting(PlanReport.NotEvaluated::family)
        .contains("delayed-conquest");
    assertThat(report.trajectoriesPerPlan()).isEqualTo(TRAJECTORIES);
    assertThat(airStrike.metrics().italyOwnsFrance().n()).isEqualTo(TRAJECTORIES);
    assertThat(new Gson().toJson(report)).contains("germanyConquersFrance");

    assertThat(exporter.toJson(exporter.export(live))).isEqualTo(before);
  }

  private static OperationalPlan plan(final PlanReport report, final String id) {
    return report.plans().stream().filter(plan -> plan.id().equals(id)).findFirst().orElseThrow();
  }
}
