package org.triplea.generalstaff.odds;

/**
 * How the attacker behaves during a simulated battle. Mirrors the settings the engine's battle
 * calculator exposes; the engine itself resolves every round.
 */
public record BattleOptions(int retreatAfterRound, AirPolicy airPolicy, LandPolicy landPolicy) {
  /** Value of {@link #retreatAfterRound()} meaning the attacker never retreats by round count. */
  public static final int NEVER = -1;

  /** What the attacker does once only aircraft are left on its side. */
  public enum AirPolicy {
    /** Retreat, because aircraft alone cannot take a territory. */
    RETREAT_WHEN_ONLY_AIR_LEFT,
    /** Keep fighting until one side is gone. Used for deliberate air-only strikes. */
    FIGHT_ON
  }

  /** Whether the attacker protects one land unit so that a won battle also conquers. */
  public enum LandPolicy {
    COMMIT_ALL,
    KEEP_ONE_LAND_UNIT
  }

  /** A land attack pressed until one side is destroyed; air alone does not stay to the end. */
  public static BattleOptions fightToTheEnd() {
    return new BattleOptions(NEVER, AirPolicy.RETREAT_WHEN_ONLY_AIR_LEFT, LandPolicy.COMMIT_ALL);
  }

  /** An attack by aircraft only, pressed until the defenders or the aircraft are gone. */
  public static BattleOptions airStrike() {
    return new BattleOptions(NEVER, AirPolicy.FIGHT_ON, LandPolicy.COMMIT_ALL);
  }

  /** The same options but retreating once the given battle round has been fought. */
  public BattleOptions retreatingAfterRound(final int round) {
    if (round < 1) {
      throw new IllegalArgumentException("round must be at least 1, was " + round);
    }
    return new BattleOptions(round, airPolicy, landPolicy);
  }
}
