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
        observe --game <file> [--focus <territory>]...
            Print the position as JSON: step, players, relationships, territories. With
            --focus only the named territories and their neighbours are printed.

        history --game <file>
            Print the game's history (rounds, steps, events) as JSON. A map XML has none;
            a .tsvg save has the whole game so far.

        legal --game <file> --step <step> --from <territory> --to <territory> --type <unit>
              [--count N]
            Ask the engine whether N units of that type may move from one territory to the
            other in the named step (for example germansCombatMove). Prints the verdict.

        reach --game <file> --step <step> [--from <territory>]
            Every legal destination for each of the step player's unit groups, as JSON.

        odds paris --game <file> [--runs N] [--seed S] [--max-pools K] [--json]
            Run the Operation Paris baseline and print a one-page comparison, or the full
            report as JSON with --json. Defaults: runs 2000, seed 1, max-pools 25.

        odds strafe --game <file> --attacker <player> --defender <player> --territory <t>
              [--rounds R] [--land-step L] [--runs N] [--json]
            Sweep the attacker's strike force (aircraft alone, then more land units L at a
            time) against the territory, retreating after rounds 1..R and fighting to the
            end. Defaults: rounds 3, land-step 3, runs 1000.

      <file> is a map game XML or a .tsvg save game. Through Gradle
      (./gradlew :general-staff:run --args="...") relative paths are resolved from the
      module folder, so pass absolute paths.
      """;

  static final String ERROR_PREFIX = "error: ";
  static final String MISSING_OPTION = "%s is required";
  static final String UNKNOWN_ODDS_TARGET = "odds scenarios are 'paris' and 'strafe'";
  static final String NOT_A_NUMBER = "expected a whole number for %s, got '%s'";
  static final String UNKNOWN_TERRITORY = "unknown territory: %s";
  static final String UNKNOWN_PLAYER = "unknown player: %s";
  static final String NO_SUCH_UNITS = "no %s units of %s in %s";
  static final String VERDICT_LEGAL = "LEGAL";
  static final String VERDICT_ILLEGAL = "ILLEGAL";
}
