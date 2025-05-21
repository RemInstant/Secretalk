package org.reminstant.secretalk.client.exception;

import java.io.IOException;

public class LocalStorageWriteException extends IOException {
  public LocalStorageWriteException() {
  }

  public LocalStorageWriteException(String message) {
    super(message);
  }

  public LocalStorageWriteException(String message, Throwable cause) {
    super(message, cause);
  }

  public LocalStorageWriteException(Throwable cause) {
    super(cause);
  }
}
