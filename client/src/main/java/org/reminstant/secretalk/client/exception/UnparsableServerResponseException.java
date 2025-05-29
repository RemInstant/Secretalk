package org.reminstant.secretalk.client.exception;

import java.net.URI;

public class UnparsableServerResponseException extends ServerResponseException {

  private static final String MESSAGE_TEMPLATE = "Unparsable server answer on %s: %s";

  public UnparsableServerResponseException(String message) {
    super(message);
  }

  public UnparsableServerResponseException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnparsableServerResponseException(String request, String errorDetails) {
    super(MESSAGE_TEMPLATE.formatted(request, errorDetails));
  }

  public UnparsableServerResponseException(String request, String errorDetails, Throwable cause) {
    super(MESSAGE_TEMPLATE.formatted(request, errorDetails), cause);
  }

  public UnparsableServerResponseException(URI uri, String errorDetails) {
    super(MESSAGE_TEMPLATE.formatted(uri.toString(), errorDetails));
  }

  public UnparsableServerResponseException(URI uri, String errorDetails, Throwable cause) {
    super(MESSAGE_TEMPLATE.formatted(uri.toString(), errorDetails), cause);
  }
}
