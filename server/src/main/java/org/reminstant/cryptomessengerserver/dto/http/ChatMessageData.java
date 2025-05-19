package org.reminstant.cryptomessengerserver.dto.http;

import java.util.Arrays;
import java.util.Objects;

public record ChatMessageData(
    String messageId,
    String chatId,
    String otherUsername,
    byte[] messageData,
    String attachedFileName) {

  @SuppressWarnings("DeconstructionCanBeUsed")
  @Override
  public boolean equals(Object object) {
    return object instanceof ChatMessageData data && // NOSONAR
        Objects.equals(messageId, data.messageId) &&
        Objects.equals(chatId, data.chatId) &&
        Objects.equals(otherUsername, data.otherUsername) &&
        Arrays.equals(messageData, data.messageData) &&
        Objects.equals(attachedFileName, data.attachedFileName);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(messageId);
    result = 31 * result + Objects.hashCode(chatId);
    result = 31 * result + Objects.hashCode(otherUsername);
    result = 31 * result + Arrays.hashCode(messageData);
    result = 31 * result + Objects.hashCode(attachedFileName);
    return result;
  }

  @Override
  public String toString() {
    return "ChatMessageData{" +
        "messageId='" + messageId + '\'' +
        ", chatId='" + chatId + '\'' +
        ", otherUsername='" + otherUsername + '\'' +
        ", messageData=" + Arrays.toString(messageData) +
        ", attachedFileName='" + attachedFileName + '\'' +
        '}';
  }
}
