package org.triplea.generalstaff.staff;

import java.time.Instant;
import javax.annotation.Nullable;

/** One thing a staff player did or declined to do, with the engine's own answer. */
public record ExecutionRecord(
    String player,
    String step,
    @Nullable PlannedOrder order,
    @Nullable String error,
    String note,
    Instant at) {
  public boolean succeeded() {
    return order != null && error == null;
  }
}
