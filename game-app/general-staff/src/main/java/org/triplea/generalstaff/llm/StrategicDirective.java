package org.triplea.generalstaff.llm;

import java.util.List;

/**
 * A commander's directive in structured form, as the language model returns it (JSON field names
 * are the snake_case forms of these components). Names in it are data until {@link IntentParser}
 * has checked them against the observation.
 */
public record StrategicDirective(
    String objective,
    String actingPlayer,
    List<String> targetTerritories,
    String priority,
    List<String> hardConstraints,
    List<String> softConstraints,
    String riskTolerance,
    int planningHorizonRounds,
    List<String> delegatedTheaters,
    String approvalRequirements,
    List<String> mentionedPlayers,
    List<String> mentionedTerritories,
    List<String> clarificationQuestions) {

  /** The same directive with null lists replaced by empty ones and null strings by blanks. */
  StrategicDirective normalized() {
    return new StrategicDirective(
        blankIfNull(objective),
        blankIfNull(actingPlayer),
        emptyIfNull(targetTerritories),
        blankIfNull(priority),
        emptyIfNull(hardConstraints),
        emptyIfNull(softConstraints),
        blankIfNull(riskTolerance),
        planningHorizonRounds,
        emptyIfNull(delegatedTheaters),
        blankIfNull(approvalRequirements),
        emptyIfNull(mentionedPlayers),
        emptyIfNull(mentionedTerritories),
        emptyIfNull(clarificationQuestions));
  }

  private static String blankIfNull(final String value) {
    return value == null ? "" : value;
  }

  private static List<String> emptyIfNull(final List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
