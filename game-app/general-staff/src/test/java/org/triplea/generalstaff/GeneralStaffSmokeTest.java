package org.triplea.generalstaff;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.Territory;
import games.strategy.triplea.xml.TestMapGameData;
import org.junit.jupiter.api.Test;

/** Milestone M0 exit check: the module builds and can load the Global 1940 fixture. */
class GeneralStaffSmokeTest {
  @Test
  void loadsTheGlobal1940Fixture() {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();

    assertThat(data.getGameName()).isEqualTo("World War II G40 Balanced");
    final Territory france = data.getMap().getTerritoryOrNull("France");
    assertThat(france).isNotNull();
    assertThat(france.getOwner().getName()).isEqualTo("French");
  }
}
