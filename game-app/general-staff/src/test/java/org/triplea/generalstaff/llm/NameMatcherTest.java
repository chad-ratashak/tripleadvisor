package org.triplea.generalstaff.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NameMatcherTest {
  @Test
  void suggestsThePowerWhoseNameStartsTheSameWay() {
    assertThat(NameMatcher.closest("Italy", List.of("Germans", "Italians", "French"), 3))
        .containsExactly("Italians");
    assertThat(NameMatcher.closest("Germany", List.of("Germans", "Italians", "Germany"), 3))
        .containsExactly("Germany", "Germans");
  }

  @Test
  void offersNothingForNamesThatResembleNothing() {
    assertThat(NameMatcher.closest("Paris", List.of("France", "Germany", "Northern Italy"), 3))
        .isEmpty();
  }

  @Test
  void levenshteinCountsEdits() {
    assertThat(NameMatcher.levenshtein("kitten", "sitting")).isEqualTo(3);
    assertThat(NameMatcher.levenshtein("", "abc")).isEqualTo(3);
    assertThat(NameMatcher.levenshtein("same", "same")).isZero();
  }
}
