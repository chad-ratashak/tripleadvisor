package org.triplea.generalstaff.observe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameMap;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.RelationshipTracker;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.properties.GameProperties;
import games.strategy.triplea.Constants;
import games.strategy.triplea.Properties;
import games.strategy.triplea.attachments.RulesAttachment;
import games.strategy.triplea.attachments.TerritoryAttachment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.triplea.generalstaff.observe.GameObservation.Objective;
import org.triplea.generalstaff.observe.GameObservation.PlayerSummary;
import org.triplea.generalstaff.observe.GameObservation.Relationship;
import org.triplea.generalstaff.observe.GameObservation.RuleFlags;
import org.triplea.generalstaff.observe.GameObservation.TerritorySummary;

/**
 * Reads a game position into a {@link GameObservation}. Read-only: it holds the game data's read
 * lock while reading and changes nothing.
 */
public final class ObservationExporter {
  private final Gson gson =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

  /** Exports the whole map. */
  public GameObservation export(final GameData data) {
    return export(data, List.of());
  }

  /**
   * Exports the named territories and their neighbours. An empty {@code focusTerritories} exports
   * the whole map.
   *
   * @throws IllegalArgumentException if a focus territory does not exist in the game.
   */
  public GameObservation export(final GameData data, final Collection<String> focusTerritories) {
    try (GameData.Unlocker ignored = data.acquireReadLock()) {
      final GameMap map = data.getMap();
      final GameStep step = data.getSequence().getStep();
      return new GameObservation(
          data.getGameName(),
          data.getSequence().getRound(),
          step.getName(),
          Optional.ofNullable(step.getPlayerId()).map(GamePlayer::getName).orElse(null),
          ruleFlags(data.getProperties()),
          players(data),
          relationships(data),
          territories(map, selectTerritories(map, focusTerritories)));
    }
  }

  /** Serializes an observation as pretty-printed JSON. */
  public String toJson(final GameObservation observation) {
    return gson.toJson(observation);
  }

  private static Set<Territory> selectTerritories(
      final GameMap map, final Collection<String> focusTerritories) {
    final Set<Territory> selected = new TreeSet<>(Comparator.comparing(Territory::getName));
    if (focusTerritories.isEmpty()) {
      selected.addAll(map.getTerritories());
      return selected;
    }
    for (final String name : focusTerritories) {
      final Territory territory = map.getTerritoryOrNull(name);
      if (territory == null) {
        throw new IllegalArgumentException("Unknown territory: " + name);
      }
      selected.add(territory);
      selected.addAll(map.getNeighbors(territory));
    }
    return selected;
  }

  private static List<TerritorySummary> territories(
      final GameMap map, final Set<Territory> selected) {
    final List<TerritorySummary> summaries = new ArrayList<>();
    for (final Territory territory : selected) {
      final Optional<TerritoryAttachment> attachment = TerritoryAttachment.get(territory);
      final GamePlayer owner = territory.getOwner();
      summaries.add(
          new TerritorySummary(
              territory.getName(),
              owner == null || owner.isNull() ? null : owner.getName(),
              territory.isWater(),
              attachment.map(TerritoryAttachment::getProduction).orElse(0),
              attachment.flatMap(TerritoryAttachment::getCapital).orElse(null),
              attachment.map(TerritoryAttachment::getVictoryCity).orElse(0),
              map.getNeighbors(territory).stream().map(Territory::getName).sorted().toList(),
              UnitCounts.of(territory.getUnits())));
    }
    return summaries;
  }

  private static List<PlayerSummary> players(final GameData data) {
    final Optional<Resource> pus =
        data.getResourceList().getResources().stream()
            .filter(resource -> Constants.PUS.equals(resource.getName()))
            .findFirst();
    final List<Territory> territories = data.getMap().getTerritories();
    final List<PlayerSummary> summaries = new ArrayList<>();
    for (final GamePlayer player : data.getPlayerList().getPlayers()) {
      final int production =
          territories.stream()
              .filter(territory -> player.equals(territory.getOwner()))
              .mapToInt(TerritoryAttachment::getProduction)
              .sum();
      summaries.add(
          new PlayerSummary(
              player.getName(),
              pus.map(resource -> player.getResources().getQuantity(resource)).orElse(0),
              production,
              capitalsOf(player, territories),
              objectivesOf(player)));
    }
    return summaries;
  }

  private static List<String> capitalsOf(final GamePlayer player, final List<Territory> all) {
    return all.stream()
        .filter(
            territory ->
                TerritoryAttachment.get(territory)
                    .flatMap(TerritoryAttachment::getCapital)
                    .filter(player.getName()::equals)
                    .isPresent())
        .map(Territory::getName)
        .sorted()
        .toList();
  }

  private static List<Objective> objectivesOf(final GamePlayer player) {
    return RulesAttachment.getNationalObjectives(player).stream()
        .map(
            rule ->
                new Objective(
                    rule.getName(),
                    rule.getObjectiveValue(),
                    rule.getUses(),
                    rule.getEachMultiple()))
        .sorted(Comparator.comparing(Objective::name))
        .toList();
  }

  private static List<Relationship> relationships(final GameData data) {
    final RelationshipTracker tracker = data.getRelationshipTracker();
    final List<GamePlayer> players = data.getPlayerList().getPlayers();
    final List<Relationship> relationships = new ArrayList<>();
    for (int i = 0; i < players.size(); i++) {
      for (int j = i + 1; j < players.size(); j++) {
        final GamePlayer player = players.get(i);
        final GamePlayer other = players.get(j);
        relationships.add(
            new Relationship(
                player.getName(),
                other.getName(),
                tracker.getRelationshipType(player, other).getName(),
                tracker.isAtWar(player, other),
                tracker.isAllied(player, other)));
      }
    }
    return relationships;
  }

  private static RuleFlags ruleFlags(final GameProperties properties) {
    return new RuleFlags(
        Properties.getLowLuck(properties),
        Properties.getNationalObjectives(properties),
        Properties.getUsePolitics(properties),
        Properties.getWW2V3(properties),
        Properties.getAttackerRetreatPlanes(properties),
        Properties.getPartialAmphibiousRetreat(properties),
        Properties.getCaptureUnitsOnEnteringTerritory(properties),
        Properties.getUnitsCanBeDestroyedInsteadOfCaptured(properties),
        Properties.getTriggeredVictory(properties),
        Properties.getChooseAaCasualties(properties));
  }
}
