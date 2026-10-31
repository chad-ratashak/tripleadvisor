package org.triplea.generalstaff.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ProportionTest {
  @Test
  void halfOfAThousandHasATightInterval() {
    final Proportion proportion = Proportion.of(500, 1000);

    assertThat(proportion.rate()).isEqualTo(0.5);
    assertThat(proportion.ciLow()).isCloseTo(0.469, within(0.001));
    assertThat(proportion.ciHigh()).isCloseTo(0.531, within(0.001));
  }

  @Test
  void zeroSuccessesStillHaveAnUpperBound() {
    final Proportion proportion = Proportion.of(0, 1000);

    assertThat(proportion.rate()).isEqualTo(0.0);
    assertThat(proportion.ciLow()).isEqualTo(0.0);
    assertThat(proportion.ciHigh()).isCloseTo(0.0038, within(0.0002));
  }

  @Test
  void allSuccessesStillHaveALowerBound() {
    final Proportion proportion = Proportion.of(1000, 1000);

    assertThat(proportion.ciLow()).isCloseTo(0.9962, within(0.0002));
    assertThat(proportion.ciHigh()).isEqualTo(1.0);
  }

  @Test
  void smallSamplesGetWideIntervals() {
    final Proportion proportion = Proportion.of(15, 30);

    assertThat(proportion.ciHigh() - proportion.ciLow()).isGreaterThan(0.3);
  }

  @Test
  void formatsRateIntervalAndSampleSize() {
    assertThat(Proportion.of(123, 1000).format()).isEqualTo("12.3% [10.4-14.5] n=1000");
  }

  @Test
  void rejectsImpossibleInputs() {
    assertThatThrownBy(() -> Proportion.of(0, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Proportion.of(5, 4)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Proportion.of(-1, 4)).isInstanceOf(IllegalArgumentException.class);
  }
}
