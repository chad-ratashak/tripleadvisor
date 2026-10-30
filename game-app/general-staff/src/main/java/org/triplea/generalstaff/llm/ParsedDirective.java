package org.triplea.generalstaff.llm;

import java.util.List;

/**
 * The outcome of parsing a directive: the structured directive, whether every name in it exists in
 * the game, and the questions to put back to the commander. {@code valid} is false whenever a name
 * is unknown, so the planner never acts on a guess.
 */
public record ParsedDirective(
    StrategicDirective directive,
    boolean valid,
    List<String> unknownPlayers,
    List<String> unknownTerritories,
    List<String> clarifications,
    String model,
    long latencyMillis,
    String rawJson) {}
