package org.reminstant.secretalk.client.exception;

public class ModuleInitialisationException extends Exception {
  public ModuleInitialisationException() {
  }

  public ModuleInitialisationException(String message) {
    super(message);
  }

  public ModuleInitialisationException(String message, Throwable cause) {
    super(message, cause);
  }

  public ModuleInitialisationException(Throwable cause) {
    super(cause);
  }
}
