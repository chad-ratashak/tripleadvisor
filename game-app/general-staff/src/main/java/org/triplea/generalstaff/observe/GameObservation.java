package org.triplea.generalstaff.observe;

import java.util.List;
import javax.annotation.Nullable;

/**
 * A read-only snapshot of a game position in a JSON-friendly shape. The record component names are
 * the JSON field names, so changing one changes the schema that every client sees.
 */
public record GameObservation(
    String gameName,
    int round,
    @Nullable String step,
    @Nullable String stepPlayer,
    RuleFlags rules,
    List<PlayerSummary> players,
    List<Relationship> relationships,
    List<TerritorySummary> territories) {

  /** The game properties that change how battles and captures work. */
  public record RuleFlags(
      boolean lowLuck,
      boolean nationalObjectives,
      boolean usePolitics,
      boolean ww2v3,
      boolean attackerRetreatPlanes,
      boolean partialAmphibiousRetreat,
      boolean captureUnitsOnEnteringTerritory,
      boolean unitsCanBeDestroyedInsteadOfCaptured,
      boolean triggeredVictory,
      boolean chooseAaCasualties) {}

  /** One power: its treasury, the production it owns, its capitals and its objectives. */
  public record PlayerSummary(
      String name,
      int pus,
      int territoryProduction,
      List<String> capitals,
      List<Objective> objectives) {}

  /**
   * A national objective as the map defines it. Whether it is currently met is not evaluated here:
   * the engine evaluates objectives through a delegate bridge during the end-turn step.
   */
  public record Objective(String name, int value, int uses, int eachMultiple) {}

  /** The diplomatic state between two powers. */
  public record Relationship(
      String player, String other, String type, boolean atWar, boolean allied) {}

  /** One territory with its owner, economic value and the units standing in it. */
  public record TerritorySummary(
      String name,
      @Nullable String owner,
      boolean water,
      int production,
      @Nullable String capitalOf,
      int victoryCity,
      List<String> neighbors,
      List<UnitCount> units) {}

  /** A stack of identical units: how many units of one type one owner has in one place. */
  public record UnitCount(String owner, String type, int count) {}
}
