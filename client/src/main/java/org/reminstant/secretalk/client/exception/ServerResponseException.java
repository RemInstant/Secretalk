package org.reminstant.secretalk.client.exception;

public class ServerResponseException extends Exception {
  public ServerResponseException() {
  }

  public ServerResponseException(String message) {
    super(message);
  }

  public ServerResponseException(String message, Throwable cause) {
    super(message, cause);
  }

  public ServerResponseException(Throwable cause) {
    super(cause);
  }
}
