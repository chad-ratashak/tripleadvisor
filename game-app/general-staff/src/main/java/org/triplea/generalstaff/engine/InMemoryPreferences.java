package org.triplea.generalstaff.engine;

import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * A {@link java.util.prefs.Preferences} tree that lives only in this process. The engine reads its
 * client settings through a preferences node; this one hands back defaults and never touches the
 * user's real preference store.
 */
final class InMemoryPreferences extends AbstractPreferences {
  private final Map<String, String> values = new TreeMap<>();
  private final Map<String, InMemoryPreferences> children = new TreeMap<>();

  InMemoryPreferences() {
    this(null, "");
  }

  private InMemoryPreferences(final AbstractPreferences parent, final String name) {
    super(parent, name);
  }

  @Override
  protected void putSpi(final String key, final String value) {
    values.put(key, value);
  }

  @Override
  protected String getSpi(final String key) {
    return values.get(key);
  }

  @Override
  protected void removeSpi(final String key) {
    values.remove(key);
  }

  @Override
  protected void removeNodeSpi() {
    values.clear();
    children.clear();
  }

  @Override
  protected String[] keysSpi() {
    return values.keySet().toArray(String[]::new);
  }

  @Override
  protected String[] childrenNamesSpi() {
    return children.keySet().toArray(String[]::new);
  }

  @Override
  protected AbstractPreferences childSpi(final String name) {
    return children.computeIfAbsent(name, child -> new InMemoryPreferences(this, child));
  }

  @Override
  protected void syncSpi() throws BackingStoreException {
    // nothing to sync: there is no backing store
  }

  @Override
  protected void flushSpi() throws BackingStoreException {
    // nothing to flush: there is no backing store
  }
}
