package org.triplea.generalstaff.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The command line's output contracts that scripts and agents parse. */
class CliTest {
  @Test
  void legalPrintsTheVerdictWordThenJsonAndWithJsonTheJsonAlone(@TempDir final Path tempDir)
      throws Exception {
    final Path xml = tempDir.resolve("minimap.xml");
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("minimap.xml")) {
      Files.copy(in, xml);
    }
    final List<String> command =
        List.of(
            "legal",
            "--game",
            xml.toString(),
            "--step",
            "russianCombatMove",
            "--from",
            "red capitol",
            "--to",
            "red north 1",
            "--type",
            "armour");

    final Run text = run(command);
    final String[] lines = text.out().split("\n", 2);
    assertThat(lines[0]).isIn("LEGAL", "ILLEGAL");
    final JsonObject verdict = JsonParser.parseString(lines[1]).getAsJsonObject();
    assertThat(verdict.get("legal").getAsBoolean()).isEqualTo(lines[0].equals("LEGAL"));
    assertThat(text.exit()).isEqualTo(verdict.get("legal").getAsBoolean() ? 0 : 3);

    final Run json = run(concat(command, "--json"));
    final JsonObject alone = JsonParser.parseString(json.out()).getAsJsonObject();
    assertThat(alone.get("route").getAsJsonArray()).hasSize(2);
    assertThat(alone.get("legal").getAsBoolean()).isEqualTo(verdict.get("legal").getAsBoolean());
    assertThat(json.exit()).isEqualTo(text.exit());
    assertThat(json.err()).isEmpty();
  }

  @Test
  void noArgumentsPrintsUsageToStandardErrorAndExitsWithTwo() {
    final Run run = run(List.of());

    assertThat(run.exit()).isEqualTo(2);
    assertThat(run.out()).isEmpty();
    assertThat(run.err()).contains("legal --game").contains("--json");
  }

  private static List<String> concat(final List<String> command, final String option) {
    return Stream.concat(command.stream(), Stream.of(option)).toList();
  }

  private record Run(int exit, String out, String err) {}

  private static Run run(final List<String> args) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();
    final int exit =
        Cli.run(
            args,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
    return new Run(
        exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }
}
