package org.triplea.generalstaff.odds;

/**
 * WORDING ONLY. Every label a reader sees in the strafe sweep output lives here. Layout lives in
 * {@link StrafeSweepFormatter}. Change wording here and nowhere else.
 */
final class OddsText {
  private OddsText() {}

  static final String TITLE = "Strafe sweep: %s attacks %s in %s, %d runs per cell";
  static final String DEFENDERS = "Defenders";
  static final String COLUMN_FORCE = "Force";
  static final String COLUMN_RETREAT = "Retreat after";
  static final String COLUMN_DESTROYED = "All defenders destroyed";
  static final String COLUMN_HOLDS = "Defender holds";
  static final String COLUMN_DEFENDERS_LEFT = "Def. left";
  static final String COLUMN_ATTACKERS_LEFT = "Att. left";
  static final String COLUMN_TUV = "TUV swing";
  static final String TO_THE_END = "to the end";
  static final String ROUND = "round %d";
  static final String CONQUEST_NOTE =
      "With land units present, 'all defenders destroyed' means the territory is conquered.";
}
