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
import java.util.function.Function;
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

  private final Function<UUID, IBattle> battles;
  private volatile int retreatAfterRound = NEVER;

  public SimulationPlayer(final String name) {
    this(name, id -> null);
  }

  /** {@code battles} finds a battle by its id, so the round being fought can be read. */
  public SimulationPlayer(final String name, final Function<UUID, IBattle> battles) {
    super(name, LABEL);
    this.battles = battles;
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
    final IBattle battle = battles.apply(battleId);
    if (battle != null && battle.getBattleRound() >= retreatAfterRound) {
      return Optional.of(CollectionUtils.getAny(possibleTerritories));
    }
    return Optional.empty();
  }

  /**
   * The AI base class sleeps here (the client's "AI combat step pause", one second by default) so a
   * person can watch the battle. A simulation has no audience; the pause would cost a second per
   * casualty step of every sampled battle.
   */
  @Override
  public void confirmOwnCasualties(final UUID battleId, final String message) {
    // nothing to wait for
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
