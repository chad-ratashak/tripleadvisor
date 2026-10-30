package org.triplea.generalstaff.doctrine;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triplea.generalstaff.doctrine.DoctrineReconciler.Conflict;
import org.triplea.generalstaff.llm.StrategicDirective;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.ObservationExporter;

class DoctrineStoreTest {
  private static final StrategicDirective PARIS =
      new StrategicDirective(
          "Italy captures France",
          "Italians",
          List.of("France"),
          "critical",
          List.of("Germany must not conquer France"),
          List.of(),
          "balanced",
          1,
          List.of(),
          "irreversible_only",
          List.of("Italians", "Germans", "French"),
          List.of("France"),
          List.of());

  @Test
  void entriesSurviveAReopenAndReconcileAgainstTheBoard(@TempDir final Path dir) throws Exception {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final ObservationExporter exporter = new ObservationExporter();
    final GameObservation observation = exporter.export(data);
    final Path file = dir.resolve("doctrine.json");

    final DoctrineStore store = DoctrineStore.open(file);
    final DoctrineEntry entry =
        store.add(
            "Operation Paris",
            "Italy, not Germany, must take Paris.",
            PARIS,
            true,
            "",
            observation);
    assertThat(Files.exists(file)).isTrue();
    assertThat(entry.atWarPairsWhenAdded()).contains("French|Germans", "French|Italians");

    final DoctrineStore reopened = DoctrineStore.open(file);
    assertThat(reopened.entries()).hasSize(1);
    assertThat(reopened.entry(entry.id())).isPresent();
    assertThat(DoctrineReconciler.reconcile(reopened, observation)).isEmpty();

    // Italy takes France and makes peace with Britain: the reconciler notices both.
    data.performChange(
        ChangeFactory.changeOwner(
            data.getMap().getTerritoryOrNull("France"),
            data.getPlayerList().getPlayerId("Italians")));
    final List<Conflict> conflicts = DoctrineReconciler.reconcile(reopened, exporter.export(data));
    assertThat(conflicts).extracting(Conflict::kind).contains("achieved");
    assertThat(conflicts).allMatch(conflict -> conflict.entryId().equals(entry.id()));

    assertThat(reopened.remove(entry.id())).isTrue();
    assertThat(DoctrineStore.open(file).entries()).isEmpty();
  }

  @Test
  void unknownNamesAndUnparsedEntriesAreReported(@TempDir final Path dir) {
    final GameObservation observation =
        new ObservationExporter().export(TestMapGameData.GLOBAL1940.getGameData());
    final DoctrineStore store = DoctrineStore.open(dir.resolve("doctrine.json"));
    store.add("vague", "Win the war", null, true, "", null);
    final StrategicDirective wrong =
        new StrategicDirective(
            "Take Paris",
            "Italy",
            List.of("Paris"),
            "high",
            List.of(),
            List.of(),
            "balanced",
            1,
            List.of(),
            "none",
            List.of("Italy"),
            List.of("Paris"),
            List.of());
    store.add("wrong names", "Italy takes Paris", wrong, true, "", observation);

    final List<Conflict> conflicts = DoctrineReconciler.reconcile(store, observation);

    assertThat(conflicts)
        .extracting(Conflict::kind)
        .contains("unparsed", "unknown-player", "unknown-territory");
  }
}
