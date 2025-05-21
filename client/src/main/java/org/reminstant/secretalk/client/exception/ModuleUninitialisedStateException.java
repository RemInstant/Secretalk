package org.reminstant.secretalk.client.exception;

public class ModuleUninitialisedStateException extends IllegalStateException {
  public ModuleUninitialisedStateException() {
  }

  public ModuleUninitialisedStateException(String s) {
    super(s);
  }

  public ModuleUninitialisedStateException(String message, Throwable cause) {
    super(message, cause);
  }

  public ModuleUninitialisedStateException(Throwable cause) {
    super(cause);
  }
}
