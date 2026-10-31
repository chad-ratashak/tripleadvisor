package org.triplea.generalstaff.doctrine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.triplea.generalstaff.llm.StrategicDirective;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.GameObservation.PlayerSummary;
import org.triplea.generalstaff.observe.GameObservation.TerritorySummary;

/**
 * Compares each doctrine entry with the current position and reports what a commander should look
 * at: names that no longer exist, target territories already held, and powers whose war footing
 * changed since the entry was written. An enemy-held target is the normal state of an attack
 * directive and is not reported. It changes nothing.
 */
public final class DoctrineReconciler {
  /** One thing worth the commander's attention about one entry. */
  public record Conflict(String entryId, String kind, String message) {}

  private DoctrineReconciler() {}

  public static List<Conflict> reconcile(
      final DoctrineStore store, final GameObservation observation) {
    final List<Conflict> conflicts = new ArrayList<>();
    final Set<String> players =
        observation.players().stream().map(PlayerSummary::name).collect(Collectors.toSet());
    for (final DoctrineEntry entry : store.entries()) {
      final StrategicDirective directive = entry.directive();
      if (directive == null) {
        conflicts.add(new Conflict(entry.id(), "unparsed", DoctrineText.UNPARSED));
        continue;
      }
      for (final String name : directive.mentionedPlayers()) {
        if (!players.contains(name)) {
          conflicts.add(
              new Conflict(
                  entry.id(),
                  "unknown-player",
                  String.format(Locale.ROOT, DoctrineText.UNKNOWN_PLAYER, name)));
        }
      }
      for (final String target : directive.targetTerritories()) {
        final TerritorySummary territory =
            observation.territories().stream()
                .filter(candidate -> candidate.name().equals(target))
                .findFirst()
                .orElse(null);
        if (territory == null) {
          conflicts.add(
              new Conflict(
                  entry.id(),
                  "unknown-territory",
                  String.format(Locale.ROOT, DoctrineText.UNKNOWN_TERRITORY, target)));
        } else if (directive.actingPlayer().equals(territory.owner())) {
          conflicts.add(
              new Conflict(
                  entry.id(),
                  "achieved",
                  String.format(
                      Locale.ROOT, DoctrineText.ACHIEVED, directive.actingPlayer(), target)));
        }
      }
      final List<String> now = DoctrineStore.atWarPairs(observation, directive);
      for (final String pair : now) {
        if (!entry.atWarPairsWhenAdded().contains(pair)) {
          conflicts.add(
              new Conflict(
                  entry.id(),
                  "new-war",
                  String.format(Locale.ROOT, DoctrineText.NEW_WAR, pair.replace("|", " and "))));
        }
      }
      for (final String pair : entry.atWarPairsWhenAdded()) {
        if (!now.contains(pair)) {
          conflicts.add(
              new Conflict(
                  entry.id(),
                  "peace",
                  String.format(Locale.ROOT, DoctrineText.PEACE, pair.replace("|", " and "))));
        }
      }
    }
    return conflicts;
  }
}
