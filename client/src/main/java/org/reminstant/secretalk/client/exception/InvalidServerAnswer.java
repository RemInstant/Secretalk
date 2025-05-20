package org.reminstant.secretalk.client.exception;

import java.net.URI;

public class InvalidServerAnswer extends Exception {

  private static final String MESSAGE_TEMPLATE = "Invalid server answer on %s: %s";

  public InvalidServerAnswer(String message) {
    super(message);
  }

  public InvalidServerAnswer(String message, Exception ex) {
    super(message, ex);
  }

  public InvalidServerAnswer(String request, String errorDetails) {
    super(MESSAGE_TEMPLATE.formatted(request, errorDetails));
  }

  public InvalidServerAnswer(String request, String errorDetails, Exception ex) {
    super(MESSAGE_TEMPLATE.formatted(request, errorDetails), ex);
  }

  public InvalidServerAnswer(URI uri, String errorDetails) {
    super(MESSAGE_TEMPLATE.formatted(uri.toString(), errorDetails));
  }

  public InvalidServerAnswer(URI uri, String errorDetails, Exception ex) {
    super(MESSAGE_TEMPLATE.formatted(uri.toString(), errorDetails), ex);
  }
}
