package org.triplea.generalstaff.doctrine;

import java.time.Instant;
import java.util.List;
import javax.annotation.Nullable;
import org.triplea.generalstaff.llm.StrategicDirective;

/**
 * One standing instruction of the commander, kept across turns: the words, the parsed directive
 * when the staff has one, and the war-and-peace picture among the powers it names at the time it
 * was given, so later changes can be noticed.
 */
public record DoctrineEntry(
    String id,
    String title,
    String directiveText,
    @Nullable StrategicDirective directive,
    boolean standing,
    Instant addedAt,
    List<String> atWarPairsWhenAdded,
    String notes) {}
