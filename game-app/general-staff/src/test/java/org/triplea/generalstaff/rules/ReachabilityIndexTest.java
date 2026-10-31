package org.triplea.generalstaff.rules;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.rules.ReachabilityReport.Destination;
import org.triplea.generalstaff.rules.ReachabilityReport.UnitReach;

class ReachabilityIndexTest {
  @Test
  void infantryNextToFranceCanReachItButInfantryTwoAwayCannot() {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final LegalityChecker checker = LegalityChecker.forStep(data, "germansCombatMove", 1L);

    final ReachabilityReport holland =
        ReachabilityIndex.compute(checker, Optional.of("Holland Belgium"));
    assertThat(holland.step()).isEqualTo("germansCombatMove");
    assertThat(holland.player()).isEqualTo("Germans");
    assertThat(holland.nonCombatMove()).isFalse();
    final UnitReach infantry = group(holland, "infantry");
    assertThat(infantry.count()).isEqualTo(4);
    assertThat(infantry.destinations())
        .extracting(Destination::territory)
        .contains("France", "Western Germany");
    final Destination france =
        infantry.destinations().stream()
            .filter(destination -> destination.territory().equals("France"))
            .findFirst()
            .orElseThrow();
    assertThat(france.enemyOwned()).isTrue();
    assertThat(france.owner()).isEqualTo("French");
    assertThat(france.route()).containsExactly("Holland Belgium", "France");
    assertThat(infantry.reason()).isNull();

    final ReachabilityReport germany = ReachabilityIndex.compute(checker, Optional.of("Germany"));
    assertThat(group(germany, "infantry").destinations())
        .extracting(Destination::territory)
        .doesNotContain("France");
    assertThat(group(germany, "bomber").destinations())
        .extracting(Destination::territory)
        .contains("France");
    // Anti-aircraft guns can move, but not during combat movement: the group is listed with no
    // legal destination and the engine's refusal, so a planner sees both in one place.
    final UnitReach aaGuns = group(germany, "aaGun");
    assertThat(aaGuns.destinations()).isEmpty();
    assertThat(aaGuns.reason()).contains("AA guns");
  }

  @Test
  void wholeStepIndexCoversEveryMovableGroup() {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final LegalityChecker checker = LegalityChecker.forStep(data, "germansCombatMove", 1L);

    final long started = System.nanoTime();
    final ReachabilityReport report = ReachabilityIndex.compute(checker);
    final long millis = (System.nanoTime() - started) / 1_000_000;
    System.out.println(
        "Reachability for "
            + report.groups().size()
            + " German unit groups: "
            + report.groups().stream().mapToInt(group -> group.destinations().size()).sum()
            + " legal destinations in "
            + millis
            + " ms");

    assertThat(report.groups()).isNotEmpty();
    assertThat(report.groups()).allMatch(group -> group.count() > 0);
    assertThat(report.groups())
        .extracting(UnitReach::origin)
        .contains("Holland Belgium", "Western Germany", "Germany");
  }

  @Test
  void equalLengthFriendlyRoutesAreFoundWhenTheShortestRunsThroughEnemies() {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final LegalityChecker checker = LegalityChecker.forStep(data, "germansCombatMove", 1L);

    final ReachabilityReport report =
        ReachabilityIndex.compute(checker, Optional.of("Greater Southern Germany"));

    final Destination romania =
        group(report, "armour").destinations().stream()
            .filter(destination -> destination.territory().equals("Romania"))
            .findFirst()
            .orElseThrow();
    assertThat(romania.route())
        .containsExactly("Greater Southern Germany", "Slovakia Hungary", "Romania");
  }

  private static UnitReach group(final ReachabilityReport report, final String type) {
    final List<UnitReach> matches =
        report.groups().stream().filter(group -> group.type().equals(type)).toList();
    assertThat(matches).as(type).hasSize(1);
    return matches.get(0);
  }
}
