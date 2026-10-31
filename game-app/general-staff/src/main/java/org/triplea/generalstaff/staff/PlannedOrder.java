package org.triplea.generalstaff.staff;

import java.util.List;

/**
 * A movement order in the words a commander or a plan can write and the staff can check: so many
 * units of a type, along a named route. Units are picked at execution time among those that may
 * still move, so the same order text works whether or not other orders moved first.
 */
public record PlannedOrder(List<String> route, String unitType, int count) {
  public PlannedOrder {
    if (route == null || route.size() < 2) {
      throw new IllegalArgumentException("A route needs a start and at least one step");
    }
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive, was " + count);
    }
    route = List.copyOf(route);
  }

  public String from() {
    return route.get(0);
  }

  public String to() {
    return route.get(route.size() - 1);
  }
}
