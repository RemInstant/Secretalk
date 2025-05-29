package org.reminstant.secretalk.client.exception;

import java.net.URI;

public class UnexpectedServerResponseException extends ServerResponseException {

  private static final String MESSAGE_TEMPLATE = "Unexpected server answer on %s: %s";

  public UnexpectedServerResponseException(String message) {
    super(message);
  }

  public UnexpectedServerResponseException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnexpectedServerResponseException(String request, String errorDetails) {
    super(MESSAGE_TEMPLATE.formatted(request, errorDetails));
  }

  public UnexpectedServerResponseException(String request, String errorDetails, Throwable cause) {
    super(MESSAGE_TEMPLATE.formatted(request, errorDetails), cause);
  }

  public UnexpectedServerResponseException(URI uri, String errorDetails) {
    super(MESSAGE_TEMPLATE.formatted(uri.toString(), errorDetails));
  }

  public UnexpectedServerResponseException(URI uri, String errorDetails, Throwable cause) {
    super(MESSAGE_TEMPLATE.formatted(uri.toString(), errorDetails), cause);
  }
}
