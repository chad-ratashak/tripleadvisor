package org.triplea.generalstaff.llm;

/** The local language-model server could not be reached or answered with an error. */
public final class OllamaException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public OllamaException(final String message) {
    super(message);
  }

  public OllamaException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
