package org.triplea.generalstaff.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.llm.Explainer.Fact;
import org.triplea.generalstaff.odds.Proportion;
import org.triplea.generalstaff.plan.PlanReport;

class ExplainerTest {
  private static final List<Fact> FACTS =
      List.of(
          new Fact("F1", "Full attack destroys every defender", "100.0% [99.6-100.0] n=1000"),
          new Fact("F2", "Air-only strike empties France", "19.0% [16.7-21.5] n=1000"));

  @Test
  void fillsFiguresFromCodeAndFlagsInventedOnes() {
    final Explainer.RawBriefing raw =
        new Explainer.RawBriefing(
            "The full attack is certain at {F1}, the air strike rarely empties France at {F2}.",
            List.of("Roughly 95% of the time the garrison dies.", "Italy follows up at {F2}."),
            "Strike from the air only.",
            List.of("Allied reinforcement is not modelled."));

    final Briefing briefing = Explainer.fill(raw, FACTS, "fake:1b", 1234L);

    assertThat(briefing.headline())
        .isEqualTo(
            "The full attack is certain at 100.0% [99.6-100.0] n=1000, the air strike rarely empties France at 19.0% [16.7-21.5] n=1000.");
    assertThat(briefing.assessment().get(1)).contains("19.0%");
    assertThat(briefing.warnings()).hasSize(1);
    assertThat(briefing.warnings().get(0)).contains("95%");
    assertThat(briefing.model()).isEqualTo("fake:1b");
    assertThat(briefing.latencyMillis()).isEqualTo(1234L);
  }

  @Test
  void aBriefingThatCitesNoFigureIsFlagged() {
    final Explainer.RawBriefing raw =
        new Explainer.RawBriefing("All is well.", List.of(), "Carry on.", List.of());

    final Briefing briefing = Explainer.fill(raw, FACTS, "fake:1b", 0L);

    assertThat(briefing.warnings()).containsExactly(LlmText.MISSING_FIGURE);
  }

  @Test
  void unknownLabelsStayVisibleRatherThanVanishing() {
    final Explainer.RawBriefing raw =
        new Explainer.RawBriefing("See {F9}.", List.of(), "", List.of());

    final Briefing briefing = Explainer.fill(raw, FACTS, "fake:1b", 0L);

    assertThat(briefing.headline()).isEqualTo("See {F9}.");
  }

  @Test
  void briefsThroughTheServerAndRendersAsText() throws IOException {
    try (FakeOllama ollama = new FakeOllama()) {
      ollama.answerWith(
          "{\"headline\":\"Air only: {F2}.\",\"assessment\":[\"Full attack {F1}.\"],"
              + "\"recommendation\":\"Use the air.\",\"risks\":[\"Weather.\"]}");
      final Explainer explainer = new Explainer(ollama.client(), "fake:1b");

      final Briefing briefing = explainer.brief("Take Paris with Italy", FACTS);

      assertThat(briefing.headline()).isEqualTo("Air only: 19.0% [16.7-21.5] n=1000.");
      assertThat(briefing.warnings()).isEmpty();
      final String userPrompt =
          ollama
              .requests()
              .get(0)
              .getAsJsonArray("messages")
              .get(1)
              .getAsJsonObject()
              .get("content")
              .getAsString();
      assertThat(userPrompt).contains("{F1} = Full attack destroys every defender = 100.0%");
      final String text = BriefingFormatter.toText(briefing);
      assertThat(text).contains("Staff briefing").contains("Use the air.").contains("Weather.");
    }
  }

  @Test
  void planFactsNameThePowersAndTheTargetOfTheReport() {
    final PlanReport.OperationalPlan plan =
        new PlanReport.OperationalPlan(
            "strafe-3land-r1",
            "strafe",
            "a plan",
            1,
            true,
            false,
            1,
            List.of(),
            new PlanReport.Metrics(
                6,
                Proportion.of(0, 6),
                Proportion.of(0, 6),
                Proportion.of(0, 6),
                Proportion.of(6, 6),
                5.5,
                0.0,
                0.0,
                3.0,
                0.0,
                1.0),
            List.of(),
            List.of(),
            List.of());
    final PlanReport report =
        new PlanReport(
            "game",
            "Japanese",
            "Italians",
            "Chinese",
            "Yunnan",
            6,
            List.of(),
            1L,
            0.0,
            "passive",
            List.of(plan),
            List.of(),
            List.of());

    final List<Fact> facts = Explainer.facts(report);

    assertThat(facts)
        .extracting(Fact::description)
        .anyMatch(text -> text.contains("Italians own Yunnan"))
        .anyMatch(text -> text.contains("Japanese conquer Yunnan"))
        .noneMatch(text -> text.contains("Italy owns France") || text.contains("German"));
  }
}
