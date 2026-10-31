package org.triplea.generalstaff.odds;

import java.util.Locale;

/**
 * A rate observed in {@code n} independent trials, with its 95% Wilson score interval. Every
 * probability the General Staff reports travels in this shape so the sample size is never lost.
 */
public record Proportion(int successes, int n, double rate, double ciLow, double ciHigh) {
  private static final double Z = 1.959_964;

  /** Builds the proportion {@code successes / n} with a Wilson 95% interval. */
  public static Proportion of(final int successes, final int n) {
    if (n <= 0) {
      throw new IllegalArgumentException("n must be positive, was " + n);
    }
    if (successes < 0 || successes > n) {
      throw new IllegalArgumentException(
          "successes must be between 0 and n=" + n + ", was " + successes);
    }
    final double p = (double) successes / n;
    final double z2 = Z * Z;
    final double denominator = 1 + z2 / n;
    final double centre = (p + z2 / (2.0 * n)) / denominator;
    final double halfWidth = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / denominator;
    // At the extremes the Wilson bound is exactly 0 or 1; avoid rounding residue there.
    final double low = successes == 0 ? 0.0 : Math.max(0.0, centre - halfWidth);
    final double high = successes == n ? 1.0 : Math.min(1.0, centre + halfWidth);
    return new Proportion(successes, n, p, low, high);
  }

  /** Formats as {@code 12.3% [10.4-14.5] n=1000}. */
  public String format() {
    return String.format(
        Locale.ROOT, "%.1f%% [%.1f-%.1f] n=%d", rate * 100, ciLow * 100, ciHigh * 100, n);
  }
}
