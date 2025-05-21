package org.reminstant.secretalk.client.exception;

public class IllegalChatManagerRequest extends IllegalArgumentException {
  public IllegalChatManagerRequest() {
  }

  public IllegalChatManagerRequest(String s) {
    super(s);
  }

  public IllegalChatManagerRequest(String message, Throwable cause) {
    super(message, cause);
  }

  public IllegalChatManagerRequest(Throwable cause) {
    super(cause);
  }
}
