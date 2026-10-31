package org.triplea.generalstaff.sim;

import games.strategy.engine.random.IRandomSource;
import java.util.Random;

/** Dice that replay exactly for a given seed, so a simulated trajectory can be reproduced. */
public final class SeededRandomSource implements IRandomSource {
  private final Random random;

  public SeededRandomSource(final long seed) {
    random = new Random(seed);
  }

  @Override
  public int getRandom(final int max, final String annotation) {
    return random.nextInt(max);
  }

  @Override
  public int[] getRandom(final int max, final int count, final String annotation) {
    final int[] values = new int[count];
    for (int i = 0; i < count; i++) {
      values[i] = random.nextInt(max);
    }
    return values;
  }
}
