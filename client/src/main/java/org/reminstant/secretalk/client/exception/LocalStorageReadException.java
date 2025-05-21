package org.reminstant.secretalk.client.exception;

import java.io.IOException;

public class LocalStorageReadException extends IOException {
  public LocalStorageReadException() {
  }

  public LocalStorageReadException(String message) {
    super(message);
  }

  public LocalStorageReadException(String message, Throwable cause) {
    super(message, cause);
  }

  public LocalStorageReadException(Throwable cause) {
    super(cause);
  }
}
