package org.reminstant.secretalk.server.exception;

public class OccupiedUsername extends ConstraintViolationException {
  public OccupiedUsername() {
  }

  public OccupiedUsername(String message) {
    super(message);
  }

  public OccupiedUsername(String message, Throwable cause) {
    super(message, cause);
  }

  public OccupiedUsername(Throwable cause) {
    super(cause);
  }
}
