package org.triplea.generalstaff.rules;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.observe.ObservationExporter;

/**
 * The dry-run verdict must agree with what the real move delegate does, on a table of moves whose
 * legality is known from the rules. Each case uses its own units so one copy of the game serves a
 * whole step.
 */
class LegalityCheckerTest {
  private static GameData data;
  private static GamePlayer germans;
  private static String boardBefore;

  @BeforeAll
  static void loadFixture() {
    data = TestMapGameData.GLOBAL1940.getGameData();
    germans = data.getPlayerList().getPlayerId("Germans");
    final ObservationExporter exporter = new ObservationExporter();
    boardBefore = exporter.toJson(exporter.export(data));
  }

  @Test
  void combatMoveVerdictsMatchTheRulesAndTheDelegate() {
    final LegalityChecker checker = LegalityChecker.forStep(data, "germansCombatMove", 1L);
    assertThat(checker.isNonCombatMove()).isFalse();
    assertThat(checker.player().getName()).isEqualTo("Germans");

    agree(
        checker, units("Holland Belgium", "infantry", 2), route("Holland Belgium", "France"), true);
    agree(
        checker,
        units("Western Germany", "mech_infantry", 1),
        route("Western Germany", "France"),
        true);
    agree(
        checker,
        units("Holland Belgium", "infantry", 1, 2),
        route("Holland Belgium", "Western Germany"),
        true);
    agree(
        checker, units("Western Germany", "fighter", 1), route("Western Germany", "France"), true);
    agree(
        checker,
        units("Germany", "bomber", 1),
        route("Germany", "Western Germany", "France"),
        true);
    agree(
        checker,
        units("Germany", "infantry", 1),
        route("Germany", "Western Germany", "France"),
        false);
    agree(checker, units("Western Germany", "aaGun", 1), route("Western Germany", "France"), false);
  }

  @Test
  void nonCombatMoveMayNotEnterEnemyTerritory() {
    final LegalityChecker checker = LegalityChecker.forStep(data, "germansNonCombatMove", 1L);
    assertThat(checker.isNonCombatMove()).isTrue();

    agree(
        checker,
        units("Holland Belgium", "infantry", 1),
        route("Holland Belgium", "France"),
        false);
    agree(
        checker,
        units("Holland Belgium", "infantry", 1),
        route("Holland Belgium", "Western Germany"),
        true);
  }

  @Test
  void findsItsOwnRouteAndReportsMissingOnes() {
    final LegalityChecker checker = LegalityChecker.forStep(data, "germansCombatMove", 1L);

    final MoveVerdict reachable =
        checker.check(units("Germany", "bomber", 1), territory("Germany"), territory("France"));
    assertThat(reachable.legal()).isTrue();
    assertThat(reachable.route()).startsWith("Germany").endsWith("France").hasSize(3);

    final MoveVerdict tooFar =
        checker.check(units("Germany", "infantry", 1), territory("Germany"), territory("France"));
    assertThat(tooFar.legal()).isFalse();
    // Too little movement is reported by the engine as a disallowed unit, not as a route error.
    assertThat(tooFar.warnings()).isNotEmpty();
  }

  @Test
  void unknownStepsAndPlayerlessStepsAreRejected() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> LegalityChecker.forStep(data, "nowhereCombatMove", 1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nowhereCombatMove");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> LegalityChecker.forStep(data, "gameInitDelegate", 1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no player");
  }

  @Test
  void theLiveGameIsNeverTouched() {
    final LegalityChecker checker = LegalityChecker.forStep(data, "germansCombatMove", 1L);
    final List<Unit> infantry = units("Holland Belgium", "infantry", 1);
    assertThat(checker.apply(infantry, route("Holland Belgium", "France"))).isEmpty();

    assertThat(territory("Holland Belgium").getUnits()).containsAll(infantry);
    final ObservationExporter exporter = new ObservationExporter();
    assertThat(exporter.toJson(exporter.export(data))).isEqualTo(boardBefore);
  }

  /**
   * The dry run and the real delegate must give the same answer, and it must be the expected one.
   */
  private static void agree(
      final LegalityChecker checker,
      final List<Unit> units,
      final Route route,
      final boolean expectedLegal) {
    final MoveVerdict verdict = checker.check(units, route);
    final Optional<String> delegateError = checker.apply(units, route);
    assertThat(verdict.legal())
        .as("dry run for %s over %s: %s", verdict.units(), verdict.route(), verdict.error())
        .isEqualTo(expectedLegal);
    assertThat(delegateError.isEmpty())
        .as("move delegate for %s over %s: %s", verdict.units(), verdict.route(), delegateError)
        .isEqualTo(expectedLegal);
  }

  private static List<Unit> units(final String territoryName, final String type, final int count) {
    return units(territoryName, type, count, 0);
  }

  private static List<Unit> units(
      final String territoryName, final String type, final int count, final int skip) {
    final List<Unit> units =
        territory(territoryName)
            .getMatches(
                Matches.unitIsOwnedBy(germans).and(unit -> unit.getType().getName().equals(type)))
            .stream()
            .skip(skip)
            .limit(count)
            .toList();
    assertThat(units).as("%d %s in %s", count, type, territoryName).hasSize(count);
    return units;
  }

  private static Route route(final String start, final String... steps) {
    final Territory[] territories = new Territory[steps.length];
    for (int i = 0; i < steps.length; i++) {
      territories[i] = territory(steps[i]);
    }
    return new Route(territory(start), territories);
  }

  private static Territory territory(final String name) {
    final Territory territory = data.getMap().getTerritoryOrNull(name);
    assertThat(territory).as(name).isNotNull();
    return territory;
  }
}
