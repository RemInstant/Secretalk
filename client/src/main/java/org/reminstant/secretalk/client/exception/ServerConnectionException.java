package org.reminstant.secretalk.client.exception;

import java.io.IOException;

public class ServerConnectionException extends IOException {
  public ServerConnectionException() {
  }

  public ServerConnectionException(String message) {
    super(message);
  }

  public ServerConnectionException(String message, Throwable cause) {
    super(message, cause);
  }

  public ServerConnectionException(Throwable cause) {
    super(cause);
  }
}
