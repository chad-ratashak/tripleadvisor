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
  static final String MUTUAL_LOSS_NOTE =
      "When the last attacking aircraft and the last defenders fall in the same round, the engine"
          + " scores the defender as holding (the factory and airfield remain), so the first column"
          + " can run below the follow-up's emptied-France rate.";

  static final String FOLLOW_UP_TITLE =
      "Italian follow-up after the German air-only strike (no Allied move in between)";
  static final String FOLLOW_UP_SUCCESS = "Italy owns France at the end of its turn";
  static final String FOLLOW_UP_WALK_IN = "France emptied by the air strike (Italian walk-in)";
  static final String FOLLOW_UP_UNRESOLVED =
      "Rare survivor sets not simulated, counted as failures";
  static final String FOLLOW_UP_COLUMNS = "French survivors -> occurrences, Italian attack result";

  static final String EXPECTED_LOSSES = "Expected losses";

  static final String VIOLATION_CONQUEST =
      "%s conquer %s in %s of trajectories; the directive allows at most %.1f%%.";
  static final String WARNING_THIN_EVIDENCE =
      "%d of %d trajectories broke the constraint, so the true rate could still be as high as"
          + " %.1f%% (95%% bound) against a tolerated %.1f%%; more trajectories narrow the bound.";
  static final String WARNING_THIN_EVIDENCE_ZERO =
      "%d of %d trajectories broke the constraint, so the true rate could still be as high as"
          + " %.1f%% (95%% bound); a tolerance of zero cannot be confirmed by any finite sample, so"
          + " state the chance you would accept.";
  static final String SAME_POWER =
      "The attacking power and the ally must be different powers; both were %s.";
  static final String NOT_EVALUATED_OWNED = "%s already hold %s; there is nothing to attack.";
  static final String NOT_EVALUATED_NOT_AT_WAR = "%s are not at war with %s, who hold %s.";
  static final String NOT_EVALUATED_NO_FORCE =
      "No units of the %s stand within range of %s (land units next to it, aircraft that can fly"
          + " there and back), so this family has nothing to move.";
  static final String NOT_EVALUATED_NO_AIRCRAFT =
      "No aircraft of the %s can fly to %s and back, so this family has nothing to move.";
  static final String NOT_EVALUATED_DUPLICATE =
      "Only %d land units of the %s stand next to %s, so this family orders the same force as %s.";
  static final String NOT_EVALUATED_SAME_FORCE =
      "This family orders the same force and retreat as %s.";
  static final String NOT_EVALUATED_DELAYED =
      "Strike on the turn of the %s and attack on the next turn of the %s: needs a full round of"
          + " every power's turn, including the purchases and placement of the %s.";
  static final String WARNING_THIN_REJECTION =
      "%d of %d trajectories broke the constraint, but the true rate could still be as low as"
          + " %.1f%% (95%% bound) against a tolerated %.1f%%; more trajectories could clear this"
          + " plan.";

  static final String FAMILY_ALLY_ALONE = "No action by the %s; the %s attack the full garrison.";
  static final String FAMILY_AIR_STRIKE = "Aircraft of the %s only%s.";
  static final String FAMILY_STRAFE = "Aircraft of the %s plus their %d cheapest land units%s.";
  static final String FAMILY_STRAFE_ALL_LAND =
      "Aircraft of the %s plus all %d of their land units in range%s.";
  static final String FAMILY_STRAFE_NO_AIR =
      "The %d cheapest land units of the %s (no aircraft in range)%s.";
  static final String FAMILY_STRAFE_NO_AIR_ALL_LAND =
      "All %d land units of the %s in range (no aircraft in range)%s.";
  static final String FAMILY_FULL_ATTACK = "The whole strike force of the %s%s.";
  static final String FAMILY_RETREAT_AFTER = ", retreating after round %d";
  static final String FAMILY_FIGHT_ON = ", fighting to the end";
  static final String FAMILY_ALLY_ATTACKS = " The %s attack if %s is still enemy-held.";
  static final String FAMILY_ALLY_HOLDS = " The %s do not attack.";
  static final String FAMILY_ALLY_NO_ATTACK = " The %s have no attack to make here.";
  static final String VIOLATION_ORDERS_REFUSED =
      "The engine refused some of the plan's orders (all accepted in %s of trajectories).";

  static final String PLAN_TITLE =
      "Candidate plans ranked by sampled trajectories: %s strike, %s take %s";
  static final String PLAN_TRAJECTORIES = "Trajectories per plan";
  static final String PLAN_TOLERANCE = "Tolerated chance that the attacker conquers the target";
  static final String PLAN_INTERVENING = "Intervening power's move between the two turns";
  static final String PLAN_INTERVENING_ORDERS = "Intervening orders";
  static final String PLAN_COLUMN_RANK = "rank";
  static final String PLAN_COLUMN_PLAN = "plan";
  static final String PLAN_COLUMN_ORDERS = "orders ok";
  static final String PLAN_COLUMN_ATTACKER_TUV = "att. TUV lost";
  static final String PLAN_COLUMN_ATTACKER_AIRCRAFT = "att. aircraft lost";
  static final String PLAN_COLUMN_ALLY_TUV = "ally TUV lost";
  static final String PLAN_COLUMN_ALLY_PU = "ally PU gain";
  static final String PLAN_COLUMN_VERDICT = "verdict";
  static final String COLUMN_ALLY_OWNS = "%s own %s";
  static final String COLUMN_ATTACKER_CONQUERS = "%s conquer %s";
  static final String PLAN_FEASIBLE = "keeps the constraint";
  static final String PLAN_FEASIBLE_THIN = "keeps the constraint (unconfirmed by the 95% bound)";
  static final String PLAN_INFEASIBLE = "REJECTED";
  static final String PLAN_INFEASIBLE_THIN = "REJECTED (unconfirmed by the 95% bound)";
  static final String PLAN_NOTHING_RANKED =
      "No plan family could be sampled on this position; see Not evaluated below.";
  static final String PLAN_RANKS_OVERLAP =
      "Rank %d is not separated from %s on '%s': their 95%% intervals overlap, so their order rests"
          + " on the attacker's losses until more trajectories separate them.";
  static final String RANK_ONE = "rank %d";
  static final String RANK_RANGE = "ranks %d to %d";
  static final String PLAN_ORDERS = "Orders of the top plan";
  static final String PLAN_NOT_EVALUATED = "Not evaluated";
  static final String PLAN_NONE_FEASIBLE =
      "No candidate keeps the constraint; the least violating one is listed first.";

  /** Templates: %1$s the attacker, %2$s the ally, %3$s the defender, %4$s the target. */
  static final List<String> PLAN_ASSUMPTIONS =
      List.of(
          "Each trajectory is a fresh copy of the game. The combat move and battle of the %1$s, the"
              + " intervening power's non-combat move, and the combat move and battle of the %2$s"
              + " are run by the engine's own delegates with seeded dice; purchases, placement,"
              + " end-of-turn income and the other powers' turns are skipped.",
          "Orders for the %1$s move the chosen force into %4$s along the first route the engine"
              + " accepts, friendly ground first; the retreat round is answered by the staff's"
              + " stand-in player exactly when the engine asks.",
          "Orders for the %2$s move their strike force into %4$s whenever %4$s is still"
              + " enemy-held, and are skipped when the %1$s already own it.",
          "The PU gain of the %2$s is the treasury change at capture (what the %3$s held), not"
              + " income.",
          "Figures exclude infrastructure. Rates carry 95%% Wilson intervals; the sample size is"
              + " the number of trajectories per plan. Ranks whose intervals overlap are not"
              + " separated at this sample size.",
          "After each battle the mover's non-combat move flies every aircraft that cannot stay"
              + " where it is (a territory taken this turn, a sea zone without a carrier) to the"
              + " nearest own or allied territory the engine accepts; aircraft with no such place"
              + " are removed by the engine, as in the real game.",
          "The seed fixes the dice. Unit identities are drawn anew each time a game file is"
              + " loaded and the engine breaks some of its ties by identity, so a repeated run can"
              + " differ slightly in losses and, once in a while, in the outcome of one"
              + " trajectory.",
          "The delayed-conquest family (strike on the turn of the %1$s, attack on the next turn of"
              + " the %2$s) needs a full round of all powers' turns and is listed as not evaluated.");

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
