package org.triplea.generalstaff.api;

/**
 * WORDING ONLY. Everything the command line prints as prose lives here. Argument handling lives in
 * {@link Cli}; change wording here and nowhere else.
 */
final class CliText {
  private CliText() {}

  static final String USAGE =
      """
      General Staff command line (read-only; nothing here changes a game).

      Commands:
        observe --game <file.xml|file.tsvg> [--focus <territory>]...
            Print the position as JSON. With --focus, only the named territories and
            their neighbours are printed; without it, the whole map.

        odds paris --game <file.xml|file.tsvg> [--runs N] [--seed S] [--max-pools K] [--json]
            Run the Operation Paris baseline and print a one-page comparison, or the
            full report as JSON with --json. Defaults: runs 2000, seed 1, max-pools 25.

      Through Gradle (./gradlew :general-staff:run --args="...") relative paths are resolved
      from the module folder, so pass absolute paths.
      """;

  static final String ERROR_PREFIX = "error: ";
  static final String MISSING_GAME = "--game <file> is required";
  static final String UNKNOWN_ODDS_TARGET = "the only odds scenario so far is 'paris'";
  static final String NOT_A_NUMBER = "expected a whole number for %s, got '%s'";
}
