package org.reminstant.secretalk.server.exception;

import java.io.IOException;

public class LocalFileStorageException extends IOException {
  public LocalFileStorageException() {
  }

  public LocalFileStorageException(String message) {
    super(message);
  }

  public LocalFileStorageException(String message, Throwable cause) {
    super(message, cause);
  }

  public LocalFileStorageException(Throwable cause) {
    super(cause);
  }
}
