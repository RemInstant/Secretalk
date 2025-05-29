package org.reminstant.secretalk.server.exception;

public class InvalidUsernameException extends ConstraintViolationException {
  public InvalidUsernameException() {
  }

  public InvalidUsernameException(String message) {
    super(message);
  }

  public InvalidUsernameException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidUsernameException(Throwable cause) {
    super(cause);
  }
}
