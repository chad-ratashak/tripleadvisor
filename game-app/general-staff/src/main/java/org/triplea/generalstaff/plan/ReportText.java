package org.triplea.generalstaff.plan;

import java.util.List;

/**
 * WORDING ONLY. Every sentence or label a reader sees in the Operation Paris report lives here, in
 * reading order. Layout lives in {@link ReportFormatter}; the numbers come from {@link
 * OperationParisBaseline}. Change wording here and nowhere else.
 */
final class ReportText {
  private ReportText() {}

  static final String TITLE = "Operation Paris: baseline odds from the engine's battle calculator";
  static final String GAME = "Game";
  static final String RUNS = "Runs per scenario";
  static final String SEED = "Sampling seed";
  static final String FRENCH_GARRISON = "French garrison in France";
  static final String GERMAN_FORCE = "German strike force considered";
  static final String ITALIAN_FORCE = "Italian strike force considered";

  static final String COLUMN_SCENARIO = "Scenario";
  static final String COLUMN_ATTACKER_WINS = "Attacker destroys all defenders";
  static final String COLUMN_DEFENDER_HOLDS = "Defender holds";
  static final String COLUMN_ROUNDS = "Rounds";
  static final String COLUMN_DEFENDERS_LEFT = "Defenders left";
  static final String COLUMN_ATTACKERS_LEFT = "Attackers left";
  static final String COLUMN_TUV_SWING = "TUV swing";

  static final String SCENARIO_FULL = "German full attack, fight to the end";
  static final String SCENARIO_AIR_ONLY = "German air-only strike (cannot conquer)";
  static final String SCENARIO_STRAFE = "German strafe, retreat after round %d";
  static final String SCENARIO_ITALY_ALONE = "Italy attacks the full garrison alone";

  static final String STRAFE_NOTE =
      "For a strafe, the first column is the chance that every defender died before the retreat,"
          + " which with land units present means an unintended German conquest.";

  static final String FOLLOW_UP_TITLE =
      "Italian follow-up after the German air-only strike (no Allied move in between)";
  static final String FOLLOW_UP_SUCCESS = "Italy owns France at the end of its turn";
  static final String FOLLOW_UP_WALK_IN = "France emptied by the air strike (Italian walk-in)";
  static final String FOLLOW_UP_UNRESOLVED =
      "Rare survivor sets not simulated, counted as failures";
  static final String FOLLOW_UP_COLUMNS = "French survivors -> occurrences, Italian attack result";

  static final String EXPECTED_LOSSES = "Expected losses";

  static final String VIOLATION_GERMAN_CONQUEST =
      "Germany conquers France in %s of trajectories; the directive allows at most %.1f%%.";
  static final String VIOLATION_ORDERS_REFUSED =
      "The engine refused some of the plan's orders (all accepted in %s of trajectories).";

  static final String PLAN_TITLE =
      "Operation Paris: candidate plans ranked by sampled trajectories";
  static final String PLAN_TRAJECTORIES = "Trajectories per plan";
  static final String PLAN_TOLERANCE = "Tolerated chance of a German conquest";
  static final String PLAN_INTERVENING = "Allied move between the German and Italian turns";
  static final String PLAN_COLUMNS =
      "rank  plan                          Italy owns France             Germany conquers France       orders ok  Ger. TUV lost  Ger. aircraft lost  Ita. TUV lost  Ita. PU gain";
  static final String PLAN_FEASIBLE = "keeps the constraint";
  static final String PLAN_INFEASIBLE = "REJECTED";
  static final String PLAN_ORDERS = "Orders of the top plan";
  static final String PLAN_NOT_EVALUATED = "Not evaluated";
  static final String PLAN_NONE_FEASIBLE =
      "No candidate keeps the constraint; the least violating one is listed first.";
  static final List<String> PLAN_ASSUMPTIONS =
      List.of(
          "Each trajectory is a fresh copy of the game. The German combat move, German battle,"
              + " the intervening power's non-combat move, the Italian combat move and the Italian"
              + " battle are run by the engine's own delegates with seeded dice; purchases, placement,"
              + " end-of-turn income and the other powers' turns are skipped.",
          "German orders move the chosen force into France along the shortest route; the retreat"
              + " round is answered by the staff's stand-in player exactly when the engine asks.",
          "Italian orders move Italy's strike force into France whenever France is still enemy-held,"
              + " and are skipped when Germany already owns it.",
          "The Italian PU gain is the treasury change at capture (the French bank), not income.",
          "Figures exclude infrastructure. Rates carry 95% Wilson intervals; the sample size is the"
              + " number of trajectories per plan.",
          "The delayed-conquest family (strike on the German turn, attack on Italy's next turn)"
              + " needs a full round of all powers' turns and is listed as not evaluated.");

  static final String ASSUMPTIONS_TITLE = "Assumptions and limits";
  static final List<String> ASSUMPTIONS =
      List.of(
          "Strike forces: every land unit of the attacking power standing in a territory next to"
              + " France that may move in combat, plus every aircraft of that power within half"
              + " its movement range of France, so that it can return.",
          "The French garrison is every unit present in France; the engine decides which of them"
              + " fight. Figures for units left or lost exclude infrastructure such as factories"
              + " and airfields.",
          "Every rate comes from the engine's battle calculator, which rolls real battle rounds"
              + " with dice; each rate shows its sample size and a 95% Wilson interval.",
          "Italian follow-up: for each German air-only run, the French survivors are re-created"
              + " at full strength and attacked by the Italian force; an emptied France counts as"
              + " an Italian walk-in. No Allied move between the German and Italian turns is"
              + " modelled, and survivor sets beyond the most frequent ones are not simulated"
              + " and count as failures. These limits make the follow-up rate optimistic.",
          "A strafe retreats the attacker after the given round; the engine chooses casualties"
              + " with its default ordering.",
          "No move legality is checked here: the forces are selected by position and range only."
              + " Legality checking is the next milestone.");
}
