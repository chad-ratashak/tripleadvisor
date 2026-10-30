package org.triplea.generalstaff.sim;

import games.strategy.engine.data.Change;
import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GameData;
import java.util.ArrayList;
import java.util.List;

/**
 * Every change applied to a sandbox, in order, so any suffix can be undone with the engine's own
 * {@link Change#invert()}. Delegate-internal state (pending battles, moves made) is not covered; a
 * fork from a snapshot is the safe way to branch across steps.
 */
public final class ChangeJournal {
  private final List<Change> changes = new ArrayList<>();

  /** Applies the change to the data and remembers it. */
  public void apply(final GameData data, final Change change) {
    data.performChange(change);
    changes.add(change);
  }

  /** A position in the journal to roll back to later. */
  public int mark() {
    return changes.size();
  }

  public int size() {
    return changes.size();
  }

  /** Undoes everything applied after {@code mark}, newest first. */
  public void rollbackTo(final GameData data, final int mark) {
    if (mark < 0 || mark > changes.size()) {
      throw new IllegalArgumentException(
          "Mark " + mark + " is outside the journal (0.." + changes.size() + ")");
    }
    final List<Change> suffix = new ArrayList<>(changes.subList(mark, changes.size()));
    if (suffix.isEmpty()) {
      return;
    }
    data.performChange(new CompositeChange(suffix).invert());
    changes.subList(mark, changes.size()).clear();
  }

  /** Undoes everything in the journal. */
  public void rollback(final GameData data) {
    rollbackTo(data, 0);
  }
}
