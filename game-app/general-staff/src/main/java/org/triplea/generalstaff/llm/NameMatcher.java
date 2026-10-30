package org.triplea.generalstaff.llm;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Finds the game names closest to a name a model or a person wrote, for clarification hints. */
final class NameMatcher {
  private static final double MAX_RELATIVE_DISTANCE = 0.4;
  private static final int MIN_SHARED_PREFIX = 4;

  private NameMatcher() {}

  /** Up to {@code limit} candidates close to {@code name}, best first; empty when none is close. */
  static List<String> closest(
      final String name, final Collection<String> candidates, final int limit) {
    final String wanted = name.toLowerCase(Locale.ROOT).strip();
    return candidates.stream()
        .filter(candidate -> isClose(wanted, candidate.toLowerCase(Locale.ROOT)))
        .sorted(
            Comparator.comparingDouble(
                    (String candidate) ->
                        relativeDistance(wanted, candidate.toLowerCase(Locale.ROOT)))
                .thenComparing(candidate -> candidate))
        .limit(limit)
        .toList();
  }

  private static boolean isClose(final String wanted, final String candidate) {
    return sharedPrefix(wanted, candidate) >= MIN_SHARED_PREFIX
        || candidate.contains(wanted)
        || relativeDistance(wanted, candidate) <= MAX_RELATIVE_DISTANCE;
  }

  private static int sharedPrefix(final String a, final String b) {
    int i = 0;
    while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
      i++;
    }
    return i;
  }

  private static double relativeDistance(final String a, final String b) {
    final int longest = Math.max(a.length(), b.length());
    return longest == 0 ? 0.0 : (double) levenshtein(a, b) / longest;
  }

  static int levenshtein(final String a, final String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        final int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
        current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), substitution);
      }
      final int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }
}
