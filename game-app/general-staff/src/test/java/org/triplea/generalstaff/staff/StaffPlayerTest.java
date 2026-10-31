package org.triplea.generalstaff.staff;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triplea.generalstaff.staff.ExecutionSession.Others;

/**
 * Drives a whole hosted Global 1940 game in this JVM with the staff as Germany and idle stand-ins
 * for every other power: the staff proposes, the gate decides, the real delegates execute.
 */
class StaffPlayerTest {
  @Test
  void approvedOrdersAreExecutedThroughTheRealDelegateAfterASave(@TempDir final Path work)
      throws Exception {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    try (ExecutionSession session =
        ExecutionSession.start(data, "Germans", Others.IDLE, ApprovalGate.autoApprove(), work)) {
      session.setOrders(
          List.of(
              new PlannedStep(
                  "germansCombatMove",
                  List.of(
                      new PlannedOrder(List.of("Holland Belgium", "France"), "infantry", 2),
                      new PlannedOrder(
                          List.of("Germany", "Western Germany", "France"), "infantry", 1)),
                  1)));

      final int steps = session.runUntilStep("germansNonCombatMove", 60);
      final ExecutionSession.Status status = session.status();

      assertThat(steps).isGreaterThan(3);
      assertThat(status.step()).isEqualTo("germansNonCombatMove");
      assertThat(status.gameOver()).isFalse();
      final List<ExecutionRecord> records = session.staff().records();
      assertThat(records)
          .anyMatch(record -> record.note().startsWith("saved before-germansCombatMove"));
      assertThat(records)
          .filteredOn(record -> record.order() != null && record.order().count() == 2)
          .singleElement()
          .satisfies(record -> assertThat(record.succeeded()).isTrue());
      assertThat(records)
          .filteredOn(record -> record.order() != null && record.order().from().equals("Germany"))
          .singleElement()
          .satisfies(record -> assertThat(record.error()).isNotBlank());
      final Territory france = data.getMap().getTerritoryOrNull("France");
      final long germanInfantryInFrance =
          france.getUnits().stream()
              .filter(Matches.unitIsOwnedBy(data.getPlayerList().getPlayerId("Germans")))
              .count();
      final Territory holland = data.getMap().getTerritoryOrNull("Holland Belgium");
      final long germanInfantryInHolland =
          holland.getMatches(unit -> unit.getType().getName().equals("infantry")).size();
      // Two infantry left Holland Belgium; after the battle they are in France (retreat is
      // impossible
      // for a two-unit attack that lost) or dead.
      assertThat(germanInfantryInHolland).isEqualTo(2);
      assertThat(germanInfantryInFrance).isBetween(0L, 2L);
      try (Stream<Path> saves = Files.list(work.resolve("saves"))) {
        assertThat(saves.map(path -> path.getFileName().toString()))
            .anyMatch(name -> name.startsWith("before-germansCombatMove"));
      }
      final AfterActionReport report = session.report();
      assertThat(report.players()).containsExactly("Germans");
      assertThat(report.decisions()).isNotEmpty();
      assertThat(report.history().rounds()).isNotEmpty();
      assertThat(session.host().launchAction().messages()).isEmpty();
    }
  }

  @Test
  void aManualGateBlocksTheEngineAndARejectionMovesNothing(@TempDir final Path work)
      throws Exception {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final ApprovalGate gate = ApprovalGate.manual(Duration.ofMinutes(5));
    try (ExecutionSession session =
        ExecutionSession.start(data, "Germans", Others.IDLE, gate, work)) {
      session.setOrders(
          List.of(
              new PlannedStep(
                  "germansCombatMove",
                  List.of(new PlannedOrder(List.of("Holland Belgium", "France"), "infantry", 2)),
                  PlannedStep.NEVER_RETREAT)));
      session.runInBackground(1);

      final ApprovalGate.Pending pending = gate.awaitPending(Duration.ofMinutes(2)).orElseThrow();
      assertThat(pending.step()).isEqualTo("germansCombatMove");
      assertThat(pending.player()).isEqualTo("Germans");
      assertThat(pending.proposed()).hasSize(1);
      assertThat(session.status().step()).isEqualTo("germansCombatMove");

      assertThat(gate.reject("test")).isTrue();
      final long deadline = System.currentTimeMillis() + 120_000;
      while (session.status().step().equals("germansCombatMove")
          && System.currentTimeMillis() < deadline) {
        Thread.sleep(100);
      }
      // The engine thread is now past the combat move or waiting at the non-combat move gate.
      assertThat(session.staff().records())
          .anyMatch(record -> record.note().equals("rejected by test"));
      assertThat(
              data.getMap()
                  .getTerritoryOrNull("Holland Belgium")
                  .getMatches(unit -> unit.getType().getName().equals("infantry")))
          .hasSize(4);
    }
  }

  @Test
  void aircraftThatCannotStayAreFlownHomeAtTheNonCombatMove(@TempDir final Path work)
      throws Exception {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    try (ExecutionSession session =
        ExecutionSession.start(data, "Germans", Others.IDLE, ApprovalGate.autoApprove(), work)) {
      // Two fighters fly over an empty sea zone in the combat move; nothing to fight, nowhere to
      // stay. Without landing orders the engine would remove them when the non-combat move ends.
      session.setOrders(
          List.of(
              new PlannedStep(
                  "germansCombatMove",
                  List.of(
                      new PlannedOrder(List.of("Western Germany", "112 Sea Zone"), "fighter", 2)),
                  PlannedStep.NEVER_RETREAT)));

      session.runUntilStep("germansPlace", 60);

      final GamePlayer germans = data.getPlayerList().getPlayerId("Germans");
      final Territory seaZone = data.getMap().getTerritoryOrNull("112 Sea Zone");
      assertThat(seaZone.getUnits()).isEmpty();
      final List<ExecutionRecord> landings =
          session.staff().records().stream()
              .filter(record -> record.step().equals("germansNonCombatMove"))
              .filter(record -> record.order() != null)
              .toList();
      assertThat(landings).isNotEmpty();
      assertThat(landings).allSatisfy(record -> assertThat(record.succeeded()).isTrue());
      assertThat(landings.stream().mapToInt(record -> record.order().count()).sum()).isEqualTo(2);
      assertThat(landings)
          .allSatisfy(
              record -> {
                assertThat(record.order().unitType()).isEqualTo("fighter");
                assertThat(record.order().from()).isEqualTo("112 Sea Zone");
                final Territory home = data.getMap().getTerritoryOrNull(record.order().to());
                assertThat(home.isWater()).isFalse();
                assertThat(home.getOwner()).isEqualTo(germans);
              });
      final ApprovalGate.Decision landingDecision =
          session.gate().history().stream()
              .filter(decision -> decision.step().equals("germansNonCombatMove"))
              .findFirst()
              .orElseThrow();
      assertThat(landingDecision.orders()).hasSize(1);
      assertThat(landingDecision.orders().get(0).count()).isEqualTo(2);
    }
  }
}
