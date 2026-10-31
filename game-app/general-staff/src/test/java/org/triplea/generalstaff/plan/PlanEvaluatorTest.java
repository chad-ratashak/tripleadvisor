package org.triplea.generalstaff.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.odds.Proportion;
import org.triplea.generalstaff.plan.PlanReport.Metrics;
import org.triplea.generalstaff.plan.PlanReport.OperationalPlan;

/**
 * Feasibility is judged on the observed rate, but a verdict from a handful of trajectories is
 * marked as unconfirmed until the 95% bound of the constraint rate settles it: the upper bound
 * within tolerance for a kept plan, the lower bound above tolerance for a rejected one.
 */
class PlanEvaluatorTest {
  @Test
  void aFeasibleVerdictOnFewTrajectoriesIsMarkedUnconfirmed() {
    final List<OperationalPlan> ranked =
        PlanEvaluator.rank(
            List.of(plan("broken", Proportion.of(2, 6)), plan("thin", Proportion.of(0, 6))), 0.0);

    final OperationalPlan thin = ranked.get(0);
    assertThat(thin.id()).isEqualTo("thin");
    assertThat(thin.rank()).isEqualTo(1);
    assertThat(thin.feasible()).isTrue();
    assertThat(thin.evidenceSufficient()).isFalse();
    assertThat(thin.constraintViolations()).isEmpty();
    // At a zero tolerance no finite sample can confirm the verdict, and the warning says so.
    assertThat(thin.warnings())
        .singleElement()
        .asString()
        .contains("0 of 6")
        .contains("39.0%")
        .contains("cannot be confirmed by any finite sample");

    // Two conquests in six put the lower bound (9.7%) above a zero tolerance: a confirmed
    // rejection.
    final OperationalPlan broken = ranked.get(1);
    assertThat(broken.feasible()).isFalse();
    assertThat(broken.evidenceSufficient()).isTrue();
    assertThat(broken.constraintViolations()).singleElement().asString().contains("Germany");
    assertThat(broken.warnings()).isEmpty();
  }

  @Test
  void aBoundWithinToleranceIsSufficientEvidence() {
    final OperationalPlan sure =
        PlanEvaluator.rank(List.of(plan("sure", Proportion.of(0, 400))), 0.02).get(0);

    assertThat(sure.feasible()).isTrue();
    assertThat(sure.evidenceSufficient()).isTrue();
    assertThat(sure.warnings()).isEmpty();
  }

  @Test
  void aBoundAboveANonZeroToleranceSaysMoreTrajectoriesNarrowIt() {
    final OperationalPlan thin =
        PlanEvaluator.rank(List.of(plan("thin", Proportion.of(0, 6))), 0.10).get(0);

    assertThat(thin.feasible()).isTrue();
    assertThat(thin.evidenceSufficient()).isFalse();
    assertThat(thin.warnings())
        .singleElement()
        .asString()
        .contains("10.0%")
        .contains("more trajectories narrow the bound")
        .doesNotContain("finite sample");
  }

  @Test
  void aRejectionTheLowerBoundDoesNotConfirmIsMarkedUnconfirmed() {
    final Proportion conquest = Proportion.of(2, 24);
    final OperationalPlan plan = PlanEvaluator.rank(List.of(plan("close", conquest)), 0.05).get(0);

    assertThat(conquest.rate()).isGreaterThan(0.05);
    assertThat(conquest.ciLow()).isLessThan(0.05);
    assertThat(plan.feasible()).isFalse();
    assertThat(plan.evidenceSufficient()).isFalse();
    assertThat(plan.constraintViolations()).hasSize(1);
    assertThat(plan.warnings())
        .singleElement()
        .asString()
        .contains("2 of 24")
        .contains("as low as")
        .contains("5.0%");
  }

  @Test
  void aRejectionForRefusedOrdersIsSettledWhateverTheConquestSample() {
    final OperationalPlan refused =
        PlanEvaluator.rank(
                List.of(
                    plan("refused", Proportion.of(0, 6), Proportion.of(6, 6), Proportion.of(3, 6))),
                0.0)
            .get(0);

    assertThat(refused.feasible()).isFalse();
    assertThat(refused.evidenceSufficient()).isTrue();
    assertThat(refused.constraintViolations()).singleElement().asString().contains("refused");
    assertThat(refused.warnings()).isEmpty();
  }

  @Test
  void violationTextNamesTheAttackerAndTheTargetItWasGiven() {
    final OperationalPlan plan =
        PlanEvaluator.rank(List.of(plan("x", Proportion.of(3, 4))), 0.0, "Japanese", "Hawaii")
            .get(0);

    assertThat(plan.constraintViolations())
        .singleElement()
        .asString()
        .startsWith("Japanese conquer Hawaii");
  }

  @Test
  void ranksTheSampleDoesNotSeparateAreGroupedBehindTheFirstTheyOverlap() {
    // 90% [77.0-96.0], 75% [59.8-85.8], 60% [44.6-73.6]: the second overlaps the first, the third
    // overlaps the second but not the first, so it starts a run of its own and is left out.
    final List<OperationalPlan> ranked =
        PlanEvaluator.rank(
            List.of(
                plan("a", Proportion.of(0, 40), Proportion.of(36, 40)),
                plan("b", Proportion.of(0, 40), Proportion.of(30, 40)),
                plan("c", Proportion.of(0, 40), Proportion.of(24, 40)),
                plan("rejected", Proportion.of(40, 40), Proportion.of(0, 40))),
            0.0);

    assertThat(ranked).extracting(OperationalPlan::id).containsExactly("a", "b", "c", "rejected");
    assertThat(PlanEvaluator.unseparatedRanks(ranked)).containsExactly(List.of(1, 2));
    assertThat(PlanEvaluator.unseparatedRanks(List.of(ranked.get(0), ranked.get(2)))).isEmpty();
  }

  private static OperationalPlan plan(final String id, final Proportion conquest) {
    final int n = conquest.n();
    return plan(id, conquest, Proportion.of(n - conquest.successes(), n));
  }

  private static OperationalPlan plan(
      final String id, final Proportion conquest, final Proportion allyOwns) {
    return plan(id, conquest, allyOwns, Proportion.of(conquest.n(), conquest.n()));
  }

  private static OperationalPlan plan(
      final String id,
      final Proportion conquest,
      final Proportion allyOwns,
      final Proportion ordersAccepted) {
    final int n = conquest.n();
    return new OperationalPlan(
        id,
        id,
        id,
        0,
        false,
        false,
        -1,
        List.of(),
        new Metrics(
            n,
            allyOwns,
            conquest,
            Proportion.of(0, n),
            ordersAccepted,
            10.0,
            1.0,
            2.0,
            3.0,
            4.0,
            1.5),
        List.of(),
        List.of(),
        List.of());
  }
}
