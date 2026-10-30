package org.triplea.generalstaff.rules;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Unit;
import java.util.Collection;

/**
 * Something that can say whether units may move along a route in the step it is positioned at,
 * using the engine's validator: a {@link LegalityChecker} on its own copy, or a sandbox mid-turn.
 */
public interface MoveJudge {
  /** The copy of the game the judge works on; units and territories in verdicts belong to it. */
  GameData gameCopy();

  /** The step's player, as an object of the copy. */
  GamePlayer player();

  String stepName();

  boolean isNonCombatMove();

  /** Dry run: validates without changing the copy. */
  MoveVerdict check(Collection<Unit> units, Route route);
}
