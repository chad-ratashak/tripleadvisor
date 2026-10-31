package org.triplea.generalstaff.staff;

import java.util.List;

/**
 * The orders for one step of the staff player's turn, the retreat round to answer with when the
 * engine asks during that turn's battles ({@code -1} never retreats), and whether whatever the
 * orders do not cover is handed to the engine's Fast AI for the rest of the step (hybrid mode).
 */
public record PlannedStep(
    String step, List<PlannedOrder> orders, int retreatAfterRound, boolean delegateRemainder) {
  public static final int NEVER_RETREAT = -1;

  public PlannedStep {
    orders = orders == null ? List.of() : List.copyOf(orders);
  }

  public PlannedStep(
      final String step, final List<PlannedOrder> orders, final int retreatAfterRound) {
    this(step, orders, retreatAfterRound, false);
  }

  public static PlannedStep none(final String step) {
    return new PlannedStep(step, List.of(), NEVER_RETREAT, false);
  }
}
