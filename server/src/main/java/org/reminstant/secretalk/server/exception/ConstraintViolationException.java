package org.reminstant.secretalk.server.exception;

public class ConstraintViolationException extends Exception {
  public ConstraintViolationException() {
  }

  public ConstraintViolationException(String message) {
    super(message);
  }

  public ConstraintViolationException(String message, Throwable cause) {
    super(message, cause);
  }

  public ConstraintViolationException(Throwable cause) {
    super(cause);
  }
}
