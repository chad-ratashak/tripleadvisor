package org.triplea.generalstaff.engine;

import games.strategy.triplea.settings.ClientSetting;

/**
 * The engine reads {@link ClientSetting}s while deserializing units and fighting battles, and
 * throws if no host has initialized them. The desktop and bot hosts do that with the user's real
 * preference store; a library or test host gets in-memory defaults from here instead.
 */
public final class EngineSettings {
  private EngineSettings() {}

  /** Installs in-memory client settings unless a host has already initialized them. */
  public static synchronized void ensureInitialized() {
    if (!initialized()) {
      ClientSetting.setPreferences(new InMemoryPreferences());
    }
  }

  private static boolean initialized() {
    try {
      // The engine offers no query for this; reading any setting is the only probe.
      ClientSetting.showSerializeFeatures.getValue();
      return true;
    } catch (final IllegalStateException notInitialized) {
      return false;
    }
  }
}
