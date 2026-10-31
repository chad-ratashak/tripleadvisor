package org.triplea.generalstaff.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                    new Family("air-strike-r1", "air only, retreat after round 1", 0, 1, true),
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

    assertThat(report.plans()).hasSize(4);
    assertThat(report.plans()).extracting(OperationalPlan::rank).containsExactly(1, 2, 3, 4);
    assertThat(report.attacker()).isEqualTo("Germans");
    assertThat(report.ally()).isEqualTo("Italians");
    assertThat(report.target()).isEqualTo("France");
    assertThat(report.interveningOrders()).isEmpty();
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
    assertThat(report.plans().get(3).id()).isEqualTo("full-attack");
    // With no tolerance a zero rate on six trajectories can never be confirmed by its bound.
    assertThat(airStrike.evidenceSufficient()).isFalse();
    assertThat(airStrike.warnings()).singleElement().asString().contains("0 of " + TRAJECTORIES);
    assertThat(fullAttack.warnings()).isEmpty();
    // The retreat policy now sees the lone battle: an air strike limited to one round fights one.
    final OperationalPlan oneRound = plan(report, "air-strike-r1");
    assertThat(oneRound.metrics().meanGermanBattleRounds()).isEqualTo(1.0);
    assertThat(oneRound.metrics().meanGermanAircraftLost()).isLessThan(8.0);
    assertThat(airStrike.metrics().meanGermanBattleRounds()).isBetween(1.0, 2.0);
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

  @Test
  void rejectsTheSamePowerAsAttackerAndAlly() {
    final GameData live = TestMapGameData.GLOBAL1940.getGameData();
    assertThatThrownBy(
            () ->
                new OperationParisPlanner(
                    live, "Germans", "Germans", "French", "France", Settings.defaults()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Germans");
  }

  @Test
  void familiesWithNothingToAttackAreListedAsNotEvaluatedInsteadOfAsEmptyPlans() {
    final GameData live = TestMapGameData.GLOBAL1940.getGameData();
    final Settings settings = Settings.defaults().withTrajectories(2);

    final PlanReport held =
        new OperationParisPlanner(live, "Germans", "Italians", "French", "Germany", settings)
            .plan();
    assertThat(held.plans()).isEmpty();
    assertThat(held.notEvaluated())
        .hasSize(settings.families().size() + 1)
        .allSatisfy(
            entry ->
                assertThat(entry.reason())
                    .containsAnyOf("already hold Germany", "needs a full round"));
    assertThat(PlanFormatter.toText(held)).doesNotContain("No candidate keeps the constraint");

    final PlanReport farAway =
        new OperationParisPlanner(
                live, "Germans", "Italians", "Americans", "Hawaiian Islands", settings)
            .plan();
    assertThat(farAway.plans()).isEmpty();
    assertThat(farAway.notEvaluated())
        .filteredOn(entry -> !entry.family().equals("delayed-conquest"))
        .allSatisfy(
            entry -> assertThat(entry.reason()).containsAnyOf("not at war", "within range"));
  }

  @Test
  void familiesThatWouldOrderTheSameForceCollapseAndTheWordingNamesThePowersGiven() {
    final GameData live = TestMapGameData.GLOBAL1940.getGameData();
    final int landInRange =
        ForceSelection.landStrikeForce(
                live,
                live.getPlayerList().getPlayerId("Japanese"),
                live.getMap().getTerritoryOrNull("Yunnan"))
            .size();
    // The fixture, like the live file, has three infantry and one artillery at Kwangsi.
    assertThat(landInRange).isEqualTo(4);

    final PlanReport report =
        new OperationParisPlanner(
                live,
                "Japanese",
                "Italians",
                "Chinese",
                "Yunnan",
                Settings.defaults().withTrajectories(2).withSeed(9L))
            .plan();
    final String text = PlanFormatter.toText(report);
    System.out.println(text);

    assertThat(text)
        .contains("Japanese strike, Italians take Yunnan")
        .doesNotContain("German")
        .doesNotContain("Italy ")
        .doesNotContain("France");
    // Every template placeholder was filled in; the percent sign of "95% Wilson" is wording.
    assertThat(report.assumptions())
        .allSatisfy(line -> assertThat(line).doesNotContain("$s").doesNotContain("%%"));
    assertThat(report.assumptions().get(0)).contains("Japanese").contains("Italians");
    assertThat(report.assumptions().get(1)).contains("Yunnan");
    // Six and nine land units cannot be ordered where four stand: the six-unit families are sampled
    // as "all 4" and the nine-unit families are listed as the same force as them.
    assertThat(report.plans())
        .extracting(OperationalPlan::id)
        .containsExactlyInAnyOrder(
            "strafe-3land-r1",
            "strafe-3land-r2",
            "strafe-6land-r1",
            "strafe-6land-r2",
            "full-attack");
    assertThat(plan(report, "strafe-6land-r1").description())
        .isEqualTo(
            "All 4 land units of the Japanese in range (no aircraft in range), retreating after"
                + " round 1. The Italians have no attack to make here.");
    assertThat(plan(report, "strafe-3land-r1").description())
        .isEqualTo(
            "The 3 cheapest land units of the Japanese (no aircraft in range), retreating after"
                + " round 1. The Italians have no attack to make here.");
    assertThat(plan(report, "full-attack").description())
        .isEqualTo(
            "The whole strike force of the Japanese, fighting to the end. The Italians have no"
                + " attack to make here.");
    assertThat(notEvaluated(report, "strafe-9land-r1"))
        .isEqualTo(
            "Only 4 land units of the Japanese stand next to Yunnan, so this family orders the same"
                + " force as strafe-6land-r1.");
    assertThat(notEvaluated(report, "strafe-9land-r2")).endsWith("same force as strafe-6land-r2.");
    assertThat(notEvaluated(report, "air-strike-r1"))
        .isEqualTo(
            "No aircraft of the Japanese can fly to Yunnan and back, so this family has nothing to"
                + " move.");
    assertThat(notEvaluated(report, "italy-alone")).containsAnyOf("not at war", "within range");
    // Three infantry can hit at most three times in one round, so four Chinese infantry are never
    // all killed: the "target emptied" metric must count the attacker's enemies, not the ally's
    // (the Italians are neutral to the Chinese, so every Chinese unit would have "not counted").
    assertThat(plan(report, "strafe-3land-r1").metrics().franceEmptiedByGermany().successes())
        .isZero();
    assertThat(plan(report, "strafe-3land-r1").metrics().italyOwnsFrance().successes()).isZero();
    assertThat(report.notEvaluated())
        .filteredOn(entry -> entry.family().equals("delayed-conquest"))
        .singleElement()
        .satisfies(
            entry ->
                assertThat(entry.reason())
                    .contains("turn of the Japanese")
                    .contains("next turn of the Italians")
                    .contains("placement of the Chinese"));
  }

  @Test
  void defaultFamiliesOnParisAreWordedForGermansItaliansAndFrance() {
    final GameData live = TestMapGameData.GLOBAL1940.getGameData();

    final PlanReport report =
        OperationParisPlanner.forGlobal1940(
                live, Settings.defaults().withTrajectories(2).withSeed(4L))
            .plan();

    assertThat(plan(report, "italy-alone").description())
        .isEqualTo("No action by the Germans; the Italians attack the full garrison.");
    assertThat(plan(report, "air-strike-r2").description())
        .isEqualTo(
            "Aircraft of the Germans only, retreating after round 2. The Italians attack if France"
                + " is still enemy-held.");
    assertThat(plan(report, "strafe-3land-r1").description())
        .isEqualTo(
            "Aircraft of the Germans plus their 3 cheapest land units, retreating after round 1."
                + " The Italians attack if France is still enemy-held.");
    assertThat(plan(report, "full-attack").description())
        .isEqualTo(
            "The whole strike force of the Germans, fighting to the end. The Italians attack if"
                + " France is still enemy-held.");
    assertThat(report.plans()).extracting(OperationalPlan::id).doesNotHaveDuplicates();
    assertThat(PlanFormatter.toText(report)).doesNotContain("Germans conquers");
  }

  private static OperationalPlan plan(final PlanReport report, final String id) {
    return report.plans().stream().filter(plan -> plan.id().equals(id)).findFirst().orElseThrow();
  }

  private static String notEvaluated(final PlanReport report, final String family) {
    return report.notEvaluated().stream()
        .filter(entry -> entry.family().equals(family))
        .findFirst()
        .orElseThrow()
        .reason();
  }
}
