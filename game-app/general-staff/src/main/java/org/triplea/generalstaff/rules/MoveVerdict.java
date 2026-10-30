package org.triplea.generalstaff.rules;

import java.util.List;
import javax.annotation.Nullable;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;

/**
 * The engine's answer to "may these units move along this route in this step?". {@code legal} is
 * true only when the move validator reports no error, no disallowed unit and no unresolved unit,
 * which is exactly when the real move delegate would perform the move.
 */
public record MoveVerdict(
    String step,
    boolean nonCombatMove,
    String player,
    List<UnitCount> units,
    List<String> route,
    boolean legal,
    @Nullable String error,
    List<String> warnings) {}
