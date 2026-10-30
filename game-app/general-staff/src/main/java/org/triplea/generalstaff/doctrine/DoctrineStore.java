package org.triplea.generalstaff.doctrine;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.triplea.generalstaff.json.Json;
import org.triplea.generalstaff.llm.StrategicDirective;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.GameObservation.Relationship;

/**
 * The commander's doctrine for a campaign: a JSON file of standing directives, read and written
 * whole. Doctrine is never applied silently; {@link DoctrineReconciler} surfaces what has changed
 * since each entry was written.
 */
public final class DoctrineStore {
  private final Path file;
  private final List<DoctrineEntry> entries = new ArrayList<>();

  private DoctrineStore(final Path file) {
    this.file = file;
  }

  /** Opens (or starts) the doctrine file. */
  public static DoctrineStore open(final Path file) {
    final DoctrineStore store = new DoctrineStore(file);
    if (Files.exists(file)) {
      try {
        final List<DoctrineEntry> loaded =
            Json.GSON.fromJson(
                Files.readString(file, StandardCharsets.UTF_8),
                new TypeToken<List<DoctrineEntry>>() {}.getType());
        if (loaded != null) {
          store.entries.addAll(loaded);
        }
      } catch (final IOException e) {
        throw new IllegalStateException("Could not read doctrine file " + file, e);
      }
    }
    return store;
  }

  public Path file() {
    return file;
  }

  public List<DoctrineEntry> entries() {
    return List.copyOf(entries);
  }

  public Optional<DoctrineEntry> entry(final String id) {
    return entries.stream().filter(entry -> entry.id().equals(id)).findFirst();
  }

  /**
   * Adds a directive, remembering which named powers were at war with each other in {@code
   * observation} (if given) so the reconciler can notice changes later.
   */
  public DoctrineEntry add(
      final String title,
      final String directiveText,
      @Nullable final StrategicDirective directive,
      final boolean standing,
      final String notes,
      @Nullable final GameObservation observation) {
    final DoctrineEntry entry =
        new DoctrineEntry(
            UUID.randomUUID().toString().substring(0, 8),
            title,
            directiveText,
            directive,
            standing,
            Instant.now(),
            observation == null || directive == null
                ? List.of()
                : atWarPairs(observation, directive),
            notes == null ? "" : notes);
    entries.add(entry);
    save();
    return entry;
  }

  public boolean remove(final String id) {
    final boolean removed = entries.removeIf(entry -> entry.id().equals(id));
    if (removed) {
      save();
    }
    return removed;
  }

  static List<String> atWarPairs(
      final GameObservation observation, final StrategicDirective directive) {
    final List<String> named = new ArrayList<>(directive.mentionedPlayers());
    if (!directive.actingPlayer().isBlank() && !named.contains(directive.actingPlayer())) {
      named.add(directive.actingPlayer());
    }
    final List<String> pairs = new ArrayList<>();
    for (final Relationship relationship : observation.relationships()) {
      if (relationship.atWar()
          && (named.contains(relationship.player()) || named.contains(relationship.other()))) {
        pairs.add(pair(relationship.player(), relationship.other()));
      }
    }
    return pairs.stream().sorted().toList();
  }

  static String pair(final String a, final String b) {
    return a.compareTo(b) <= 0
        ? String.format(Locale.ROOT, "%s|%s", a, b)
        : String.format(Locale.ROOT, "%s|%s", b, a);
  }

  private void save() {
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      Files.writeString(file, Json.GSON.toJson(entries), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new IllegalStateException("Could not write doctrine file " + file, e);
    }
  }
}
