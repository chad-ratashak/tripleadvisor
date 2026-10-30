package org.triplea.generalstaff.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.triplea.xml.TestMapGameData;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameLoadingTest {
  @Test
  void loadsAMapXmlFromDisk(@TempDir final Path tempDir) throws IOException {
    final Path xml = tempDir.resolve("minimap.xml");
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("minimap.xml")) {
      Files.copy(in, xml);
    }

    final GameData data = GameLoading.load(xml);

    assertThat(data.getGameName()).isEqualTo("Minimap");
    assertThat(data.getMap().getTerritoryOrNull("blue capitol")).isNotNull();
  }

  @Test
  void loadsASaveGameWrittenByTheEngine(@TempDir final Path tempDir) throws IOException {
    final GameData original = TestMapGameData.MINIMAP.getGameData();
    final Path save = tempDir.resolve("minimap.tsvg");
    try (OutputStream out = Files.newOutputStream(save)) {
      GameDataManager.saveGame(out, original);
    }

    final GameData loaded = GameLoading.load(save);

    assertThat(loaded.getGameName()).isEqualTo(original.getGameName());
    assertThat(loaded.getUnits().getUnits()).hasSameSizeAs(original.getUnits().getUnits());
  }

  @Test
  void rejectsMissingFilesAndUnknownExtensions(@TempDir final Path tempDir) throws IOException {
    assertThatThrownBy(() -> GameLoading.load(tempDir.resolve("missing.xml")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
    final Path other = tempDir.resolve("game.txt");
    Files.writeString(other, "not a game");
    assertThatThrownBy(() -> GameLoading.load(other))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(".tsvg");
  }
}
