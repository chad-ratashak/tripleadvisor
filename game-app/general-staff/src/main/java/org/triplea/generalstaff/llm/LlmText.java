package org.triplea.generalstaff.llm;

/**
 * WORDING ONLY. Every prompt the General Staff sends to the language model, every sentence it adds
 * to the model's answers, and every label of the model evaluation live here in reading order.
 * Request building lives in {@link IntentParser} and {@link Explainer}. Change wording here and
 * nowhere else.
 */
final class LlmText {
  private LlmText() {}

  static final String INTENT_SYSTEM =
      """
      You are the chief of staff of a player in a TripleA game (an Axis & Allies style strategy
      game). The player gives you a strategic directive in plain language. Turn it into the JSON
      structure you are asked for. Rules:
      - Use only player names and territory names from the lists below, spelled exactly. If the
        directive names a place or power that is not on the lists, choose the closest name on the
        list when the meaning is clear (a capital city stands for the territory that holds it), and
        otherwise put a question in clarification_questions instead of guessing.
      - acting_player is the power that is to achieve the objective. mentioned_players and
        mentioned_territories list every power and territory the directive refers to.
      - hard_constraints are things that must not happen; soft_constraints are preferences.
      - Do not invent numbers, odds, unit positions or routes: you have none. The game engine
        computes those later.
      - Write plain English, one sentence per entry.

      Game: %s. Round %d, step %s.
      Players (with treasuries): %s
      Capitals: %s
      Territories: %s
      """;

  static final String INTENT_USER = "Directive: %s";

  static final String EXPLAIN_SYSTEM =
      """
      You are the chief of staff briefing the commander on an operation the engine has already
      evaluated. Write the briefing from the FACTS below and nothing else. Rules:
      - Never write a number, percentage or count yourself. Refer to each figure only by its label
        in curly braces, exactly as given, for example {F3}; the staff office fills the figures in.
      - Say what the figures mean for the commander's intent, what the plan should and should
        not do, and the main risks, in plain English.
      - Keep the headline to one sentence and each assessment or risk entry to one sentence.
      - If the facts do not support a recommendation, say so instead of inventing one.
      """;

  static final String EXPLAIN_USER =
      """
      Commander's intent: %s

      FACTS
      %s
      """;

  static final String FACT_LINE = "{%s} = %s = %s";

  static final String UNKNOWN_PLAYER =
      "The directive names '%s', which is not a power in this game. Powers are: %s.";
  static final String UNKNOWN_TERRITORY =
      "The directive names '%s', which is not a territory on this map.";
  static final String CAPITAL_HINT = " Capitals on this map: %s.";
  static final String INVENTED_NUMBERS =
      "The model wrote figures of its own (%s); only the labelled figures are the engine's.";
  static final String MISSING_FIGURE = "The model cited none of the figures.";
  static final String SUGGESTION = " Did you mean %s?";

  static final String BRIEFING_TITLE = "Staff briefing";
  static final String BRIEFING_ASSESSMENT = "Assessment";
  static final String BRIEFING_RECOMMENDATION = "Recommendation";
  static final String BRIEFING_RISKS = "Risks";
  static final String BRIEFING_WARNINGS = "Warnings about this briefing";

  static final String EVAL_TITLE = "Directive parsing evaluation: model %s, %d directives";
  static final String EVAL_SCHEMA = "Answers that were valid JSON of the right shape";
  static final String EVAL_NAMES = "Answers whose every name exists in the game";
  static final String EVAL_LATENCY = "Mean latency";
  static final String EVAL_COLUMNS = "ok  names  seconds  acting player -> targets | directive";
  static final String EVAL_NOTE =
      "Twenty directives give wide intervals; treat differences between models as hints until"
          + " the set is larger.";
}
