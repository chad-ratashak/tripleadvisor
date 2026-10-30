package org.triplea.generalstaff.odds;

import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.delegate.Matches;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.triplea.java.collections.IntegerMap;

/**
 * Builds nested subsets of a strike force, so a sweep can show how outcomes scale with commitment.
 */
public final class ForceSubsets {
  private ForceSubsets() {}

  /** A named subset of a force. */
  public record Force(String label, List<Unit> units) {}

  /**
   * The aircraft alone, then the aircraft plus the first {@code step}, {@code 2 * step}, ... land
   * units, then everything. Land units are committed cheapest first (by TUV cost, then type name),
   * so each step adds the next-cheapest units.
   */
  public static List<Force> byLandCount(
      final Collection<Unit> force, final int step, final IntegerMap<UnitType> costs) {
    if (step <= 0) {
      throw new IllegalArgumentException("step must be positive, was " + step);
    }
    final List<Unit> air = force.stream().filter(Matches.unitIsAir()).toList();
    final List<Unit> land =
        force.stream()
            .filter(Matches.unitIsAir().negate())
            .sorted(
                Comparator.comparingInt((Unit unit) -> costs.getInt(unit.getType()))
                    .thenComparing(unit -> unit.getType().getName())
                    .thenComparing(unit -> unit.getId().toString()))
            .toList();
    final List<Force> forces = new ArrayList<>();
    if (!air.isEmpty()) {
      forces.add(new Force("air only", air));
    }
    for (int count = step; count < land.size(); count += step) {
      final List<Unit> units = new ArrayList<>(air);
      units.addAll(land.subList(0, count));
      forces.add(new Force("air + " + count + " land", units));
    }
    if (!land.isEmpty()) {
      final List<Unit> units = new ArrayList<>(air);
      units.addAll(land);
      forces.add(new Force("all units", units));
    }
    return forces;
  }
}
