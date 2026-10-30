package org.triplea.generalstaff.odds;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import java.util.Collection;

/**
 * One battle to estimate: who attacks whom, where, with which units, and how the attacker behaves.
 * The units must belong to the game data the {@link BattleOddsService} was built from; the engine
 * copies them into its own working copies.
 */
public record BattleScenario(
    String label,
    GamePlayer attacker,
    GamePlayer defender,
    Territory location,
    Collection<Unit> attackingUnits,
    Collection<Unit> defendingUnits,
    BattleOptions options) {}
