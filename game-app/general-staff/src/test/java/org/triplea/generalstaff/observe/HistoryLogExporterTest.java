package org.triplea.generalstaff.observe;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.history.EventChild;
import games.strategy.engine.history.HistoryWriter;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.xml.TestMapGameData;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triplea.generalstaff.engine.GameLoading;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;
import org.triplea.generalstaff.observe.HistoryLog.EventLog;
import org.triplea.generalstaff.observe.HistoryLog.StepLog;

class HistoryLogExporterTest {
  @Test
  void exportsRoundsStepsAndEventsAndSurvivesASaveGame(@TempDir final Path tempDir)
      throws IOException {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final GamePlayer germans = data.getPlayerList().getPlayerId("Germans");
    final Territory hollandBelgium = data.getMap().getTerritoryOrNull("Holland Belgium");
    final Territory france = data.getMap().getTerritoryOrNull("France");
    final List<Unit> infantry =
        hollandBelgium
            .getMatches(
                Matches.unitIsOwnedBy(germans)
                    .and(unit -> unit.getType().getName().equals("infantry")))
            .stream()
            .limit(2)
            .toList();

    final HistoryWriter writer = data.getHistory().getHistoryWriter();
    writer.startNextRound(1);
    writer.startNextStep("germansCombatMove", "move", germans, "Combat Move");
    writer.startEvent("Germans move 2 infantry from Holland Belgium to France");
    writer.setRenderingData(new MoveDescription(infantry, new Route(hollandBelgium, france)));
    writer.addChildToEvent(new EventChild("2 infantry moved", null));
    writer.startEvent("Germans roll dice");

    final HistoryLogExporter exporter = new HistoryLogExporter();
    final HistoryLog log = exporter.export(data);

    assertThat(log.gameName()).isEqualTo("World War II G40 Balanced");
    assertThat(log.rounds()).hasSize(1);
    assertThat(log.rounds().get(0).round()).isEqualTo(1);
    final StepLog step = log.rounds().get(0).steps().get(0);
    assertThat(step.step()).isEqualTo("germansCombatMove");
    assertThat(step.title()).isEqualTo("Combat Move");
    assertThat(step.player()).isEqualTo("Germans");
    assertThat(step.events()).hasSize(2);
    final EventLog move = step.events().get(0);
    assertThat(move.kind()).isEqualTo("move");
    assertThat(move.units()).containsExactly(new UnitCount("Germans", "infantry", 2));
    assertThat(move.route()).containsExactly("Holland Belgium", "France");
    assertThat(move.details()).containsExactly("2 infantry moved");
    assertThat(step.events().get(1).kind()).isNull();

    final Path save = tempDir.resolve("history.tsvg");
    try (OutputStream out = Files.newOutputStream(save)) {
      GameDataManager.saveGame(out, data);
    }
    final HistoryLog reloaded = exporter.export(GameLoading.load(save));
    assertThat(exporter.toJson(reloaded)).isEqualTo(exporter.toJson(log));
  }

  @Test
  void aFreshlyParsedGameHasNoRounds() {
    final HistoryLog log = new HistoryLogExporter().export(TestMapGameData.MINIMAP.getGameData());

    assertThat(log.gameName()).isEqualTo("Minimap");
    assertThat(log.rounds()).isEmpty();
  }
}
