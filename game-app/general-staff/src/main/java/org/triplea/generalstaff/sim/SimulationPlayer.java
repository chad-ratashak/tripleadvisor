package org.triplea.generalstaff.sim;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameState;
import games.strategy.engine.data.Territory;
import games.strategy.triplea.ai.AbstractAi;
import games.strategy.triplea.delegate.battle.IBattle;
import games.strategy.triplea.delegate.remote.IAbstractPlaceDelegate;
import games.strategy.triplea.delegate.remote.IMoveDelegate;
import games.strategy.triplea.delegate.remote.IPurchaseDelegate;
import games.strategy.triplea.delegate.remote.ITechDelegate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.triplea.java.collections.CollectionUtils;

/**
 * The player the engine talks to during a General Staff simulation. It answers the engine's
 * questions with the AI defaults (confirm moves under anti-aircraft fire, default casualty order)
 * and never initiates anything itself. Its one policy is when to retreat: never, or after a given
 * battle round, which is how a strafe is expressed.
 */
public final class SimulationPlayer extends AbstractAi {
  public static final String LABEL = "General Staff simulation";

  /** Value of the retreat round meaning "never retreat by round count". */
  public static final int NEVER = -1;

  private final Supplier<IBattle> currentBattle;
  private volatile int retreatAfterRound = NEVER;

  public SimulationPlayer(final String name) {
    this(name, () -> null);
  }

  /** {@code currentBattle} supplies the battle being fought, so the round can be read. */
  public SimulationPlayer(final String name, final Supplier<IBattle> currentBattle) {
    super(name, LABEL);
    this.currentBattle = currentBattle;
  }

  /** Retreat as soon as the battle has fought this many rounds; {@link #NEVER} to stay. */
  public void retreatAfterRound(final int round) {
    retreatAfterRound = round;
  }

  public int retreatRound() {
    return retreatAfterRound;
  }

  @Override
  public Optional<Territory> retreatQuery(
      final UUID battleId,
      final boolean submerge,
      final Territory battleTerritory,
      final Collection<Territory> possibleTerritories,
      final String message) {
    if (submerge || retreatAfterRound == NEVER || possibleTerritories.isEmpty()) {
      return Optional.empty();
    }
    final IBattle battle = currentBattle.get();
    if (battle != null && battle.getBattleRound() >= retreatAfterRound) {
      return Optional.of(CollectionUtils.getAny(possibleTerritories));
    }
    return Optional.empty();
  }

  @Override
  protected void politicalActions() {
    // a stand-in never changes diplomacy on its own
  }

  @Override
  protected void purchase(
      final boolean purchaseForBid,
      final int pusToSpend,
      final IPurchaseDelegate purchaseDelegate,
      final GameData data,
      final GamePlayer player) {
    // a simulation player makes no purchases of its own
  }

  @Override
  protected void tech(
      final ITechDelegate techDelegate, final GameData data, final GamePlayer player) {
    // a simulation player researches nothing of its own
  }

  @Override
  protected void move(
      final boolean nonCombat,
      final IMoveDelegate moveDel,
      final GameData data,
      final GamePlayer player) {
    // moves are issued by the simulator, not by the player
  }

  @Override
  protected void place(
      final boolean placeForBid,
      final IAbstractPlaceDelegate placeDelegate,
      final GameState data,
      final GamePlayer player) {
    // placements are issued by the simulator, not by the player
  }
}
