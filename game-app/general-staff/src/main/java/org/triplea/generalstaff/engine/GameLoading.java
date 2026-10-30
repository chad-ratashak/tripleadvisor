package org.triplea.generalstaff.engine;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.gameparser.GameParser;
import games.strategy.engine.framework.GameDataFileUtils;
import games.strategy.engine.framework.GameDataManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Opens a game from disk: either a map game XML or a {@code .tsvg} save game. Read-only; the file
 * is not touched after it has been parsed.
 */
public final class GameLoading {
  private GameLoading() {}

  /** Loads the game held in {@code file}, deciding by its extension how to read it. */
  public static GameData load(final Path file) {
    EngineSettings.ensureInitialized();
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("Game file not found: " + file);
    }
    final String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
    if (fileName.endsWith(".xml")) {
      return GameParser.parse(file, false)
          .orElseThrow(() -> new IllegalArgumentException("Could not parse game XML: " + file));
    }
    if (GameDataFileUtils.isCandidateFileName(fileName)) {
      return GameDataManager.loadGame(file)
          .orElseThrow(() -> new IllegalArgumentException("Could not load save game: " + file));
    }
    throw new IllegalArgumentException(
        "Expected a .xml game file or a "
            + GameDataFileUtils.getExtension()
            + " save game, got: "
            + file);
  }
}
