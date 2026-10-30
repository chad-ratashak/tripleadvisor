package org.triplea.generalstaff.llm;

/**
 * LAYOUT ONLY. Renders a {@link Briefing} as text. If you are here to change wording, you are in
 * the wrong file: see {@link LlmText}.
 */
public final class BriefingFormatter {
  private BriefingFormatter() {}

  public static String toText(final Briefing briefing) {
    final StringBuilder out = new StringBuilder();
    out.append(LlmText.BRIEFING_TITLE).append(" (").append(briefing.model()).append(")\n");
    out.append(briefing.headline()).append('\n').append('\n');
    out.append(LlmText.BRIEFING_ASSESSMENT).append('\n');
    for (final String line : briefing.assessment()) {
      out.append("  - ").append(line).append('\n');
    }
    out.append(LlmText.BRIEFING_RECOMMENDATION).append('\n');
    out.append("  ").append(briefing.recommendation()).append('\n');
    out.append(LlmText.BRIEFING_RISKS).append('\n');
    for (final String line : briefing.risks()) {
      out.append("  - ").append(line).append('\n');
    }
    if (!briefing.warnings().isEmpty()) {
      out.append(LlmText.BRIEFING_WARNINGS).append('\n');
      for (final String line : briefing.warnings()) {
        out.append("  ! ").append(line).append('\n');
      }
    }
    return out.toString();
  }
}
