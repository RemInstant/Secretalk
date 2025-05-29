package org.reminstant.secretalk.client.exception;

import java.io.IOException;

public class LocalStorageCreationException extends IOException {
  public LocalStorageCreationException() {
  }

  public LocalStorageCreationException(String message) {
    super(message);
  }

  public LocalStorageCreationException(String message, Throwable cause) {
    super(message, cause);
  }

  public LocalStorageCreationException(Throwable cause) {
    super(cause);
  }
}
