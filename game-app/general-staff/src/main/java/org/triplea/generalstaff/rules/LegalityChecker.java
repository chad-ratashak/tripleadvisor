package org.triplea.generalstaff.rules;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.framework.GameDataUtils;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.data.MoveValidationResult;
import games.strategy.triplea.delegate.move.validation.MoveValidator;
import games.strategy.triplea.delegate.remote.IMoveDelegate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.sim.StaffDelegateBridge;

/**
 * Answers move-legality questions with the engine's own {@code MoveValidator}, run on a private
 * copy of the game positioned at a named step (the validator reads the current step to decide
 * between combat and non-combat rules). The live game is never touched. Units, territories and
 * routes may be passed from the live game; they are translated into the copy by id and name.
 *
 * <p>{@link #apply} performs a move on the copy through the real move delegate, so a sequence of
 * moves can be checked with the copy's units already moved.
 */
public final class LegalityChecker implements MoveJudge {
  private final GameData copy;
  private final GameStep step;
  private final GamePlayer player;
  private final boolean nonCombat;
  private final IMoveDelegate moveDelegate;

  private LegalityChecker(
      final GameData copy,
      final GameStep step,
      final GamePlayer player,
      final boolean nonCombat,
      final IMoveDelegate moveDelegate) {
    this.copy = copy;
    this.step = step;
    this.player = player;
    this.nonCombat = nonCombat;
    this.moveDelegate = moveDelegate;
  }

  /**
   * Copies the live game with its delegates and positions the copy at the step named {@code
   * stepName} (for example {@code germansCombatMove}); {@code seed} drives any dice the copy rolls.
   */
  public static LegalityChecker forStep(
      final GameData live, final String stepName, final long seed) {
    final GameData copy =
        GameDataUtils.cloneGameData(
                live, GameDataManager.Options.builder().withDelegates(true).build())
            .orElseThrow(() -> new IllegalStateException("Could not copy the game data"));
    final GameStep step = findStep(copy, stepName);
    final GamePlayer player = step.getPlayerId();
    if (player == null) {
      throw new IllegalArgumentException("Step " + stepName + " has no player");
    }
    copy.getSequence()
        .setRoundAndStep(copy.getSequence().getRound(), step.getDisplayName(), player);
    final IMoveDelegate moveDelegate = copy.getMoveDelegate();
    moveDelegate.setDelegateBridgeAndPlayer(StaffDelegateBridge.forSimulation(copy, player, seed));
    final boolean nonCombat = GameStepPropertiesHelper.isNonCombatMove(copy, false);
    return new LegalityChecker(copy, step, player, nonCombat, moveDelegate);
  }

  @Override
  /** The private copy of the game this checker works on. */
  public GameData gameCopy() {
    return copy;
  }

  @Override
  /** The step's player, as an object of the copy. */
  public GamePlayer player() {
    return player;
  }

  @Override
  public String stepName() {
    return step.getName();
  }

  @Override
  public boolean isNonCombatMove() {
    return nonCombat;
  }

  @Override
  /** Dry run: validates the move without changing the copy. */
  public MoveVerdict check(final Collection<Unit> units, final Route route) {
    final List<Unit> copyUnits = translateUnits(units);
    final Route copyRoute = translateRoute(route);
    final MoveValidationResult result =
        new MoveValidator(copy, nonCombat)
            .validateMove(
                new MoveDescription(copyUnits, copyRoute), player, moveDelegate.getMovesMade());
    return verdict(step.getName(), nonCombat, player, copyUnits, copyRoute, result);
  }

  /**
   * Finds the cheapest passable route from {@code from} to {@code to} for the units with the map's
   * own route finder, then validates it. No route at all is reported as an illegal move.
   */
  public MoveVerdict check(final Collection<Unit> units, final Territory from, final Territory to) {
    final List<Unit> copyUnits = translateUnits(units);
    final Optional<Route> route =
        copy.getMap()
            .getRouteForUnits(
                territory(from),
                territory(to),
                Matches.territoryIsNotImpassable(),
                copyUnits,
                player);
    return route
        .map(found -> check(copyUnits, found))
        .orElseGet(
            () ->
                new MoveVerdict(
                    step.getName(),
                    nonCombat,
                    player.getName(),
                    UnitCounts.of(copyUnits),
                    List.of(from.getName(), to.getName()),
                    false,
                    "No route from " + from.getName() + " to " + to.getName(),
                    List.of()));
  }

  /**
   * Performs the move on the copy through the real move delegate. The returned error, if any, is
   * the engine's own message; an empty result means the copy's units have moved.
   */
  public Optional<String> apply(final Collection<Unit> units, final Route route) {
    return moveDelegate.performMove(
        new MoveDescription(translateUnits(units), translateRoute(route)));
  }

  /** Builds a verdict from the engine's validation result. */
  public static MoveVerdict verdict(
      final String stepName,
      final boolean nonCombat,
      final GamePlayer player,
      final List<Unit> units,
      final Route route,
      final MoveValidationResult result) {
    final List<String> warnings = new ArrayList<>();
    if (result.hasDisallowedUnits()) {
      warnings.add(result.getDisallowedUnitWarning(0));
    }
    if (result.hasUnresolvedUnits()) {
      warnings.add(result.getUnresolvedUnitWarning(0));
    }
    final boolean legal =
        !result.hasError() && !result.hasDisallowedUnits() && !result.hasUnresolvedUnits();
    return new MoveVerdict(
        stepName,
        nonCombat,
        player.getName(),
        UnitCounts.of(units),
        route.getAllTerritories().stream().map(Territory::getName).toList(),
        legal,
        result.hasError() ? result.getError() : null,
        warnings);
  }

  private static GameStep findStep(final GameData data, final String stepName) {
    for (final GameStep candidate : data.getSequence()) {
      if (stepName.equals(candidate.getName())) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("Unknown step: " + stepName);
  }

  private List<Unit> translateUnits(final Collection<Unit> units) {
    final List<Unit> translated = new ArrayList<>();
    for (final Unit unit : units) {
      final Unit inCopy = copy.getUnits().get(unit.getId());
      if (inCopy == null) {
        throw new IllegalArgumentException("Unit is not part of this game: " + unit);
      }
      translated.add(inCopy);
    }
    return translated;
  }

  private Route translateRoute(final Route route) {
    return new Route(
        territory(route.getStart()), route.getSteps().stream().map(this::territory).toList());
  }

  private Territory territory(final Territory territory) {
    final Territory inCopy = copy.getMap().getTerritoryOrNull(territory.getName());
    if (inCopy == null) {
      throw new IllegalArgumentException("Territory is not part of this game: " + territory);
    }
    return inCopy;
  }
}
