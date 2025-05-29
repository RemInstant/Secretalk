package org.reminstant.secretalk.client.exception;

public class FxControlNotFoundException extends RuntimeException {
  public FxControlNotFoundException() {
  }

  public FxControlNotFoundException(String message) {
    super(message);
  }

  public FxControlNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public FxControlNotFoundException(Throwable cause) {
    super(cause);
  }
}
