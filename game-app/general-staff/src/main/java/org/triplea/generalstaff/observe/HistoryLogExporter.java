package org.triplea.generalstaff.observe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.history.Event;
import games.strategy.engine.history.EventChild;
import games.strategy.engine.history.HistoryNode;
import games.strategy.engine.history.Round;
import games.strategy.engine.history.Step;
import games.strategy.triplea.delegate.DiceRoll;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.triplea.generalstaff.observe.HistoryLog.EventLog;
import org.triplea.generalstaff.observe.HistoryLog.RoundLog;
import org.triplea.generalstaff.observe.HistoryLog.StepLog;

/**
 * Walks the game's history tree (round, step, event, event child) into a {@link HistoryLog}. This
 * is the headless counterpart of the desktop client's history log; it reads and changes nothing.
 */
public final class HistoryLogExporter {
  private final Gson gson =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

  public HistoryLog export(final GameData data) {
    try (GameData.Unlocker ignored = data.acquireReadLock()) {
      final HistoryNode root = (HistoryNode) data.getHistory().getRoot();
      final List<RoundLog> rounds = new ArrayList<>();
      for (final HistoryNode child : children(root)) {
        if (child instanceof Round round) {
          rounds.add(new RoundLog(round.getRoundNo(), steps(round)));
        }
      }
      return new HistoryLog(data.getGameName(), rounds);
    }
  }

  public String toJson(final HistoryLog log) {
    return gson.toJson(log);
  }

  private static List<StepLog> steps(final Round round) {
    final List<StepLog> steps = new ArrayList<>();
    for (final HistoryNode child : children(round)) {
      if (child instanceof Step step) {
        steps.add(
            new StepLog(
                step.getStepName(),
                step.getTitle(),
                step.getPlayerId().map(GamePlayer::getName).orElse(null),
                events(step)));
      }
    }
    return steps;
  }

  private static List<EventLog> events(final Step step) {
    final List<EventLog> events = new ArrayList<>();
    for (final HistoryNode child : children(step)) {
      if (child instanceof Event event) {
        events.add(event(event));
      }
    }
    return events;
  }

  private static EventLog event(final Event event) {
    final Object details = event.getRenderingData();
    final List<String> childTexts =
        children(event).stream()
            .filter(EventChild.class::isInstance)
            .map(HistoryNode::getTitle)
            .toList();
    if (details instanceof MoveDescription move) {
      return new EventLog(
          event.getTitle(),
          "move",
          UnitCounts.of(move.getUnits()),
          move.getRoute().getAllTerritories().stream().map(Territory::getName).toList(),
          childTexts);
    }
    if (details instanceof Collection<?> collection
        && !collection.isEmpty()
        && collection.stream().allMatch(Unit.class::isInstance)) {
      final List<Unit> units = collection.stream().map(Unit.class::cast).toList();
      return new EventLog(event.getTitle(), "units", UnitCounts.of(units), List.of(), childTexts);
    }
    final String kind;
    if (details instanceof DiceRoll) {
      kind = "dice";
    } else if (details instanceof Territory) {
      kind = "territory";
    } else if (details == null) {
      kind = null;
    } else {
      kind = details.getClass().getSimpleName();
    }
    return new EventLog(event.getTitle(), kind, List.of(), List.of(), childTexts);
  }

  private static List<HistoryNode> children(final HistoryNode node) {
    final List<HistoryNode> children = new ArrayList<>();
    for (int i = 0; i < node.getChildCount(); i++) {
      children.add((HistoryNode) node.getChildAt(i));
    }
    return children;
  }
}
