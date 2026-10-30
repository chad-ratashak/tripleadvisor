package org.triplea.generalstaff;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triplea.generalstaff.doctrine.DoctrineReconciler;
import org.triplea.generalstaff.doctrine.DoctrineStore;
import org.triplea.generalstaff.llm.IntentParser;
import org.triplea.generalstaff.llm.ParsedDirective;
import org.triplea.generalstaff.llm.StrategicDirective;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.ObservationExporter;
import org.triplea.generalstaff.plan.OperationParisPlanner;
import org.triplea.generalstaff.plan.OperationParisPlanner.Family;
import org.triplea.generalstaff.plan.OperationParisPlanner.Settings;
import org.triplea.generalstaff.plan.PlanReport;
import org.triplea.generalstaff.sim.SimulationPlayer;
import org.triplea.generalstaff.staff.ApprovalGate;
import org.triplea.generalstaff.staff.ExecutionSession;
import org.triplea.generalstaff.staff.ExecutionSession.Others;
import org.triplea.generalstaff.staff.PlannedStep;

/**
 * The whole chain on the checked-in fixture, offline: a directive (as the model would return it) is
 * validated, filed as doctrine, planned by sampled trajectories, and the top plan's German orders
 * are executed through the real engine in a hosted game with approval, after which the doctrine is
 * reconciled against the new board.
 */
class GeneralStaffEndToEndTest {
  @Test
  void directiveToDoctrineToPlanToApprovedExecution(@TempDir final Path work) throws Exception {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final ObservationExporter exporter = new ObservationExporter();
    final GameObservation observation = exporter.export(data);

    // 1. The directive, parsed (the model's answer is canned; validation is the staff's own).
    final StrategicDirective directive =
        IntentParser.parseJson(
            "{\"objective\":\"Italy captures France while Germany only weakens the garrison\","
                + "\"acting_player\":\"Italians\",\"target_territories\":[\"France\"],\"priority\":\"critical\","
                + "\"hard_constraints\":[\"Germany must not conquer France\"],\"soft_constraints\":[\"keep German land units\"],"
                + "\"risk_tolerance\":\"balanced\",\"planning_horizon_rounds\":1,\"delegated_theaters\":[],"
                + "\"approval_requirements\":\"irreversible_only\",\"mentioned_players\":[\"Italians\",\"Germans\",\"French\"],"
                + "\"mentioned_territories\":[\"France\"],\"clarification_questions\":[]}");
    final ParsedDirective parsed =
        IntentParser.validate(directive, observation, "canned", 0L, "{}");
    assertThat(parsed.valid()).isTrue();

    // 2. Filed as doctrine; nothing to reconcile yet.
    final DoctrineStore doctrine = DoctrineStore.open(work.resolve("doctrine.json"));
    doctrine.add(
        "Operation Paris",
        "Italy, not Germany, must take Paris.",
        directive,
        true,
        "",
        observation);
    assertThat(DoctrineReconciler.reconcile(doctrine, observation)).isEmpty();

    // 3. Planned by sampled trajectories under the hard constraint.
    final PlanReport report =
        OperationParisPlanner.forGlobal1940(
                data,
                Settings.defaults()
                    .withTrajectories(4)
                    .withSeed(11L)
                    .withFamilies(
                        List.of(
                            new Family(
                                "air-strike-r2", "air only, retreat after round 2", 0, 2, true),
                            new Family(
                                "full-attack",
                                "everything",
                                Family.ALL_LAND,
                                SimulationPlayer.NEVER,
                                true))))
            .plan();
    final PlanReport.OperationalPlan top = report.plans().get(0);
    assertThat(top.feasible()).isTrue();
    assertThat(top.id()).isEqualTo("air-strike-r2");
    final List<PlannedStep> orders =
        ExecutionSession.ordersFrom(top).stream()
            .filter(step -> step.step().startsWith("germans"))
            .toList();
    assertThat(orders).hasSize(1);
    assertThat(orders.get(0).orders()).isNotEmpty();

    // 4. Executed through the real engine with approval, Germany staffed, others idle.
    try (ExecutionSession session =
        ExecutionSession.start(
            data, "Germans", Others.IDLE, ApprovalGate.autoApprove(), work.resolve("host"))) {
      session.setOrders(orders);
      session.runUntilStep("germansNonCombatMove", 60);
      final var afterAction = session.report();
      assertThat(afterAction.records()).anyMatch(record -> record.succeeded());
      assertThat(afterAction.records())
          .noneMatch(record -> record.note().equals("refused by the engine"));
      assertThat(afterAction.decisions()).isNotEmpty();
      assertThat(data.getMap().getTerritoryOrNull("France").getOwner().getName())
          .as("an air-only strike cannot conquer")
          .isEqualTo("French");

      // 5. Doctrine reconciled against the board as it now stands.
      assertThat(DoctrineReconciler.reconcile(doctrine, exporter.export(data)))
          .extracting(DoctrineReconciler.Conflict::kind)
          .doesNotContain("achieved");
    }
  }
}
