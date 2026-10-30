package org.triplea.generalstaff.observe;

import games.strategy.engine.data.Unit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;

/** Turns unit collections into sorted, owner-and-type counts. */
public final class UnitCounts {
  private UnitCounts() {}

  /** Counts {@code units} by owner and type, sorted by owner then type. */
  public static List<UnitCount> of(final Collection<Unit> units) {
    final Map<String, Map<String, Integer>> counts = new TreeMap<>();
    for (final Unit unit : units) {
      counts
          .computeIfAbsent(unit.getOwner().getName(), owner -> new TreeMap<>())
          .merge(unit.getType().getName(), 1, Integer::sum);
    }
    final List<UnitCount> result = new ArrayList<>();
    counts.forEach(
        (owner, byType) ->
            byType.forEach((type, count) -> result.add(new UnitCount(owner, type, count))));
    return List.copyOf(result);
  }

  /** The number of units in the counted stacks. */
  public static int total(final List<UnitCount> counts) {
    return counts.stream().mapToInt(UnitCount::count).sum();
  }

  /** A compact one-line description, e.g. {@code French/infantry x6, French/artillery x1}. */
  public static String describe(final List<UnitCount> counts) {
    if (counts.isEmpty()) {
      return "none";
    }
    return counts.stream()
        .map(count -> count.owner() + "/" + count.type() + " x" + count.count())
        .collect(Collectors.joining(", "));
  }
}
