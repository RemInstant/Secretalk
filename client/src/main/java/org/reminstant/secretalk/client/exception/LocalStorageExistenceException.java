package org.reminstant.secretalk.client.exception;

import java.io.IOException;

public class LocalStorageExistenceException extends IOException {
  public LocalStorageExistenceException() {
  }

  public LocalStorageExistenceException(String message) {
    super(message);
  }

  public LocalStorageExistenceException(String message, Throwable cause) {
    super(message, cause);
  }

  public LocalStorageExistenceException(Throwable cause) {
    super(cause);
  }
}
