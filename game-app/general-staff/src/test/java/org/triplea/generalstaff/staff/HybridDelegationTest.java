package org.triplea.generalstaff.staff;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triplea.generalstaff.staff.ExecutionSession.Others;

/**
 * Hybrid mode: the staff's approved orders go first, the engine's Fast AI handles the rest of the
 * step.
 */
class HybridDelegationTest {
  @Test
  void theRemainderOfAStepCanBeHandedToFastAi(@TempDir final Path work) throws Exception {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    try (ExecutionSession session =
        ExecutionSession.start(data, "Germans", Others.IDLE, ApprovalGate.autoApprove(), work)) {
      session.setOrders(
          List.of(
              new PlannedStep(
                  "germansCombatMove",
                  List.of(new PlannedOrder(List.of("Holland Belgium", "France"), "infantry", 1)),
                  PlannedStep.NEVER_RETREAT,
                  true)));

      session.runUntilStep("germansBattle", 60);

      assertThat(session.staff().records())
          .anyMatch(record -> record.note().startsWith("remainder delegated to"));
      assertThat(session.staff().records())
          .noneMatch(record -> record.note().equals("delegation failed"));
      assertThat(session.host().launchAction().messages()).isEmpty();
    }
  }
}
