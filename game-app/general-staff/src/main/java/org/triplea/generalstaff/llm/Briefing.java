package org.triplea.generalstaff.llm;

import java.util.List;

/**
 * A staff briefing written by the language model around figures the engine computed. Every figure
 * in the text came from code; {@code warnings} lists anything the model did that it should not
 * have, such as writing numbers of its own.
 */
public record Briefing(
    String headline,
    List<String> assessment,
    String recommendation,
    List<String> risks,
    List<String> warnings,
    String model,
    long latencyMillis) {}
