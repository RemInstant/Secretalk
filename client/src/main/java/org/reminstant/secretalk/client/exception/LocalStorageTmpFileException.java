package org.reminstant.secretalk.client.exception;

import java.io.IOException;

public class LocalStorageTmpFileException extends IOException {
  public LocalStorageTmpFileException() {
  }

  public LocalStorageTmpFileException(String message) {
    super(message);
  }

  public LocalStorageTmpFileException(String message, Throwable cause) {
    super(message, cause);
  }

  public LocalStorageTmpFileException(Throwable cause) {
    super(cause);
  }
}
