package org.reminstant.secretalk.client.exception;

public class ServerRequestPreparationException extends RuntimeException {
  public ServerRequestPreparationException() {
  }

  public ServerRequestPreparationException(String message) {
    super(message);
  }

  public ServerRequestPreparationException(String message, Throwable cause) {
    super(message, cause);
  }

  public ServerRequestPreparationException(Throwable cause) {
    super(cause);
  }
}
