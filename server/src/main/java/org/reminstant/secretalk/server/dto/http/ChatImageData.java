package org.reminstant.secretalk.server.dto.http;

import java.util.Arrays;
import java.util.Objects;

public record ChatImageData(
    String messageId,
    String chatId,
    String otherUsername,
    String fileName,
    byte[] imageData) {

  @SuppressWarnings("DeconstructionCanBeUsed")
  @Override
  public boolean equals(Object object) {
    return object instanceof ChatImageData data && // NOSONAR
        messageId.equals(data.messageId) &&
        chatId.equals(data.chatId) &&
        otherUsername.equals(data.otherUsername) &&
        fileName.equals(data.fileName) &&
        Arrays.equals(imageData, data.imageData);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(messageId);
    result = 31 * result + Objects.hashCode(chatId);
    result = 31 * result + Objects.hashCode(otherUsername);
    result = 31 * result + Objects.hashCode(fileName);
    result = 31 * result + Arrays.hashCode(imageData);
    return result;
  }

  @Override
  public String toString() {
    return "ChatFileData{" +
        "messageId='" + messageId + '\'' +
        ", chatId='" + chatId + '\'' +
        ", otherUsername='" + otherUsername + '\'' +
        ", fileName='" + fileName + '\'' +
        ", imageData=" + Arrays.toString(imageData) +
        '}';
  }
}
