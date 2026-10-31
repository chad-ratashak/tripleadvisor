# Directive parsing evaluation (local models)

How well does a local model turn a commander's directive into the checked JSON the planner needs?
Measured with `./gradlew :general-staff:run --args="eval --game <G40 xml> --model <model>"` over
the 20 directives in
[eval-directives.txt](../../game-app/general-staff/src/main/resources/org/triplea/generalstaff/llm/eval-directives.txt)
against the whole-map Global 1940 Balanced observation. Two things are scored per directive:

- **Shape**: the answer was valid JSON of the requested shape (Ollama's structured output makes
  this nearly always true; failures are server errors or empty answers).
- **Names**: every player and territory the answer named exists in the game. This is the hard
  part: "Italy" is not a power here ("Italians" is) and "Paris" is not a territory ("France" is).
  A miss is not a wrong answer by the staff, since the parser turns it into a question back to the
  commander; it is a measure of how often the commander will be asked.

Rates carry 95% Wilson intervals. With n = 20 an interval spans roughly 20 points, so treat the
differences between models as hints until the set is larger (house rule: oversample).

Machine: 8-core Mac, 8 GB RAM, Ollama 0.40.9, models run on the same machine as the JVM. All
models are U.S.-origin (Google, Microsoft, Meta), per the model origin policy.

## Results (2026-10-30)

| Model | Right shape | Every name real | Mean latency | Notes |
|---|---|---|---|---|
| gemma4:e2b (default) | 70.0% [48.1-85.5] n=20 | 40.0% [21.9-61.3] n=20 | 25.7 s | Mapped "Paris" to `France` in 13 of the 14 well-formed answers; wrote "Italy"/"Germany" for the powers in 3, "N/A"/"Unknown" as acting player in 3. Shape failures were six answers Gson could not map (type mismatches inside the schema's own structure). |
| phi4-mini:3.8b | 55.0% [34.2-74.2] n=20 | 20.0% [8.1-41.6] n=20 | 69.0 s | Four directives hit the five-minute client timeout (the model kept generating); when it answered it usually kept city names ("Paris") and natural power names ("Italy"), which the parser turns into questions. |
| llama3.2:3b | 80.0% [58.4-91.9] n=20 | 30.0% [14.5-51.9] n=20 | 74.7 s | Fast and well-shaped, but 10 of its 20 answers used names that are not in the game (cities, informal power names); the parser turns each into a question back. |

Latencies are not comparable across rows: gemma4 ran alone, phi4-mini while the module's tests ran,
and llama3.2 while the whole test suite ran; a quiet-machine probe put llama3.2 at about 11 s and
phi4-mini at about 10 s per short directive.

Reading: on this set the staff would have to ask the commander back for roughly six of ten
directives with the default model, mostly to confirm which power or territory a name means. That
is the intended behaviour (no guessing), and the asks are cheap, but it says a stronger local model
or a few-shot prompt with the map's own vocabulary is the next lever. The shape failures are the
more useful number to drive down: they cost a full round trip and give the commander nothing.

Per-directive detail for each run is in the JSON output (`--json`), kept with the run logs of
2026-10-30 outside the repository.

## How to rerun

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```
```bash
./gradlew :general-staff:run --args="eval --game $PWD/game-app/game-core/src/testFixtures/resources/ww2_g40_balanced.xml --model gemma4:e2b"
```

Add `--json` for the per-directive detail, or `--directives <file>` (one per line, `#` comments)
for another set.
