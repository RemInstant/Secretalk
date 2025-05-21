package org.reminstant.secretalk.client.exception;

import java.io.IOException;

public class LocalStorageDeletionException extends IOException {
  public LocalStorageDeletionException() {
  }

  public LocalStorageDeletionException(String message) {
    super(message);
  }

  public LocalStorageDeletionException(String message, Throwable cause) {
    super(message, cause);
  }

  public LocalStorageDeletionException(Throwable cause) {
    super(cause);
  }
}
