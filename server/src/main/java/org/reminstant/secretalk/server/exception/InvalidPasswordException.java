package org.reminstant.secretalk.server.exception;

public class InvalidPasswordException extends ConstraintViolationException {
  public InvalidPasswordException() {
  }

  public InvalidPasswordException(String message) {
    super(message);
  }

  public InvalidPasswordException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidPasswordException(Throwable cause) {
    super(cause);
  }
}
