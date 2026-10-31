package org.triplea.generalstaff.observe;

import java.util.List;
import javax.annotation.Nullable;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;

/** The game's history tree (rounds, steps, events) in a JSON-friendly shape. */
public record HistoryLog(String gameName, List<RoundLog> rounds) {

  /** One game round and the steps played in it. */
  public record RoundLog(int round, List<StepLog> steps) {}

  /** One step of one player's turn and what happened in it. */
  public record StepLog(
      String step, String title, @Nullable String player, List<EventLog> events) {}

  /**
   * One event. {@code kind} says what the engine attached to it: {@code move} (with units and
   * route), {@code units}, {@code dice}, {@code territory}, or null when nothing was attached.
   */
  public record EventLog(
      String title,
      @Nullable String kind,
      List<UnitCount> units,
      List<String> route,
      List<String> details) {}
}
