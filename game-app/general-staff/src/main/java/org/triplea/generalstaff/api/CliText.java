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

      Position
        observe --game <file> [--focus <territory>]...
            The position as JSON: step, players, relationships, territories. With --focus
            only the named territories and their neighbours.
        history --game <file>
            The game's history (rounds, steps, events) as JSON. A map XML has none; a .tsvg
            save has the whole game so far.

      Rules
        legal --game <file> --step <step> --from <territory> --to <territory> --type <unit>
              [--count N] [--json]
            Ask the engine whether N units of that type may move between the territories in
            the named step (for example germansCombatMove): the verdict word, then the verdict
            as JSON (with --json the JSON alone). Exit code 3 when they may not.
        reach --game <file> --step <step> [--from <territory>]
            Every legal destination of each unit group of the step's player, as JSON; a group
            with no destination carries the engine's refusal of the first candidate tried.

      Odds
        odds paris --game <file> [--runs N] [--seed S] [--max-pools K] [--json]
            The Operation Paris baseline: a one-page comparison, or the report as JSON.
            Defaults: runs 2000, seed 1, max-pools 25. The seed fixes the follow-up sampling
            only; the battle calculator rolls its own dice, so win rates move a little between runs.
        odds strafe --game <file> --attacker <player> --defender <player> --territory <t>
              [--rounds R] [--land-step L] [--runs N] [--json]
            Sweep the attacker's strike force (aircraft alone, then L more land units at a
            time) against the territory, retreating after rounds 1..R and fighting to the
            end. Defaults: rounds 3, land-step 3, runs 1000.

      Plans
        plan paris --game <file> [--trajectories N] [--seed S] [--tolerance T]
              [--intervening passive|reinforce] [--explain] [--json]
              [--attacker P] [--ally P] [--defender P] [--target T]
            Evaluate the Operation Paris plan families by sampled trajectories on cloned
            games run by the engine's own delegates, and rank them under the constraint
            that the attacker must not conquer the target (T is the tolerated chance,
            default 0). Defaults: trajectories 40, seed 1, intervening passive, Germans
            strike so that the Italians take France. The seed fixes the dice; unit identities
            are drawn anew each time a game file is loaded and the engine breaks some ties by
            identity, so a repeated run can differ slightly in losses. --explain adds a staff
            briefing from the language model.

      Language model (needs a running Ollama; OLLAMA_HOST overrides the address)
        advise --game <file> --directive "<text>" [--model M]
            Turn a commander's directive into a checked JSON directive. Exit code 3 when the
            staff has questions back instead.
        validate --game <file> (--parsed '<json>' | --parsed-file <file>) [--source NAME]
            Check a directive that something else already put into the structured form
            (the same JSON shape advise produces): every power and territory name must
            exist. No model is called. Exit code 3 when the staff has questions back.
        explain --game <file> [--intent "<text>"] [--runs N] [--seed S] [--model M] [--json]
            Run the Operation Paris baseline and have the model brief the commander on it;
            every figure in the briefing is the engine's.
        eval --game <file> [--model M] [--directives <file>] [--json]
            Parse the built-in directive set (or one directive per line from a file) and
            report how often the model's answer had the right shape and only real names.
        host --game <file> --staff <player>[,<player>] [--others idle|fast] [--orders <json>]
              [--mode auto-approve|auto-reject] [--rounds R] [--work <folder>] [--json]
            Host the game in this process with the staff playing the named powers and run it
            to the end of round R (default 1), executing the orders in the JSON file (a list
            of {"step","retreatAfterRound","orders":[{"route":[...],"unitType","count"}]})
            under the given gate mode, then print the after-action report. A manual gate
            needs the API: use serve and the /execute endpoints.
        doctrine list|add|remove|reconcile --file <doctrine.json> [--game <file>]
              [--directive "<text>"] [--title T] [--notes N] [--id ID]
            The commander's standing directives: add parses the text with the model and
            remembers who was at war; reconcile reports what has changed on the board since.
        serve [--port P] [--model M]
            Start the local JSON API (loopback only) and keep running until stopped; the
            command page is at /ui.

      <file> is a map game XML or a .tsvg save game. Through Gradle
      (./gradlew :general-staff:run --args="...") relative paths are resolved from the
      module folder, so pass absolute paths. The default model is %s.
      """;

  static final String ERROR_PREFIX = "error: ";
  static final String MISSING_OPTION = "%s is required";
  static final String UNKNOWN_ODDS_TARGET = "odds scenarios are 'paris' and 'strafe'";
  static final String UNKNOWN_PLAN_TARGET = "the only plan so far is 'paris'";
  static final String NO_EXECUTION = "no hosted game is running; start one first";
  static final String COPY_FAILED = "could not copy the game for hosting";
  static final String UNKNOWN_DOCTRINE_TARGET =
      "doctrine commands are list, add, remove and reconcile";
  static final String UNKNOWN_MODE = "mode must be manual, auto-approve or auto-reject";
  static final String UNKNOWN_OTHERS = "others must be idle or fast";
  static final String HOST_DONE = "Hosted game stopped at round %d, step %s; %d staff records.";
  static final String NOT_A_NUMBER = "expected a whole number for %s, got '%s'";
  static final String UNKNOWN_TERRITORY = "unknown territory: %s";
  static final String UNKNOWN_PLAYER = "unknown player: %s";
  static final String NO_SUCH_UNITS = "no %s units of %s in %s";
  static final String VERDICT_LEGAL = "LEGAL";
  static final String VERDICT_ILLEGAL = "ILLEGAL";
  static final String DIRECTIVE_VALID = "Directive understood; every name checks out.";
  static final String DIRECTIVE_QUESTIONS = "The staff has questions before acting:";
  static final String SERVE_STOP = "Press Ctrl+C to stop.";
}
