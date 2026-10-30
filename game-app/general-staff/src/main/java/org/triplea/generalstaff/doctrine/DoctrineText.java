package org.triplea.generalstaff.doctrine;

/**
 * WORDING ONLY. Every sentence the doctrine reconciler reports lives here. Logic lives in {@link
 * DoctrineReconciler}; change wording here and nowhere else.
 */
final class DoctrineText {
  private DoctrineText() {}

  static final String UNPARSED =
      "This entry was never parsed into a directive; parse it before relying on it.";
  static final String UNKNOWN_PLAYER =
      "The directive names '%s', which is not a power in this game.";
  static final String UNKNOWN_TERRITORY =
      "The directive targets '%s', which is not a territory on this map.";
  static final String ACHIEVED = "%s already holds %s; the objective may be complete.";
  static final String NEW_WAR = "%s are now at war; they were not when this was written.";
  static final String PEACE = "%s are no longer at war; they were when this was written.";
}
