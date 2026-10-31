package org.triplea.generalstaff.sim;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.settings.ClientSetting;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.engine.EngineSettings;

/**
 * The engine's AI base class sleeps for the client's "AI combat step pause" (a second by default)
 * every time a battle asks it to confirm casualties, so a person can follow along. A simulated
 * battle has no audience, and a planner fights hundreds of them.
 */
class SimulationPlayerTest {
  @Test
  void confirmingCasualtiesDoesNotPauseEvenWhenTheClientSettingAsksForIt() {
    EngineSettings.ensureInitialized();
    final SimulationPlayer player = new SimulationPlayer("Germans");
    final int before = ClientSetting.aiCombatStepPauseDuration.getValueOrThrow();
    ClientSetting.aiCombatStepPauseDuration.setValue(1000);
    try {
      final long started = System.nanoTime();
      player.confirmOwnCasualties(UUID.randomUUID(), "Press space");
      final Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

      assertThat(elapsed).isLessThan(Duration.ofMillis(500));
    } finally {
      ClientSetting.aiCombatStepPauseDuration.setValue(before);
    }
  }

  @Test
  void theInMemorySettingsCarryNoAiPauses() {
    EngineSettings.ensureInitialized();

    assertThat(ClientSetting.aiCombatStepPauseDuration.getValueOrThrow()).isZero();
    assertThat(ClientSetting.aiMovePauseDuration.getValueOrThrow()).isZero();
  }
}
