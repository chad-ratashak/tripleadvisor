package org.triplea.generalstaff.sim;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameState;
import games.strategy.triplea.ai.AbstractAi;
import games.strategy.triplea.delegate.remote.IAbstractPlaceDelegate;
import games.strategy.triplea.delegate.remote.IMoveDelegate;
import games.strategy.triplea.delegate.remote.IPurchaseDelegate;
import games.strategy.triplea.delegate.remote.ITechDelegate;

/**
 * The player the engine talks to during a General Staff simulation. It answers the engine's
 * questions with the AI defaults (confirm moves under anti-aircraft fire, default casualty order,
 * never retreat) and never initiates anything itself. Retreat and casualty policies arrive with the
 * trajectory simulator.
 */
public final class SimulationPlayer extends AbstractAi {
  public static final String LABEL = "General Staff simulation";

  public SimulationPlayer(final String name) {
    super(name, LABEL);
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
