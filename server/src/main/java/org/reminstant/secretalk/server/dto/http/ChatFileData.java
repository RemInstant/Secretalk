package org.reminstant.secretalk.server.dto.http;

import java.util.Arrays;
import java.util.Objects;

public record ChatFileData(
    String messageId,
    String chatId,
    String otherUsername,
    long partCount,
    long partNumber,
    byte[] fileData) {

  @SuppressWarnings("DeconstructionCanBeUsed")
  @Override
  public boolean equals(Object object) {
      return object instanceof ChatFileData data && // NOSONAR
          messageId.equals(data.messageId) &&
          chatId.equals(data.chatId) &&
          otherUsername.equals(data.otherUsername) &&
          partCount == data.partCount &&
          partNumber == data.partNumber &&
          Arrays.equals(fileData, data.fileData);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(messageId);
    result = 31 * result + Objects.hashCode(chatId);
    result = 31 * result + Objects.hashCode(otherUsername);
    result = 31 * result + Objects.hashCode(partCount);
    result = 31 * result + Objects.hashCode(partNumber);
    result = 31 * result + Arrays.hashCode(fileData);
    return result;
  }

  @Override
  public String toString() {
    return "ChatFileData{" +
        "messageId='" + messageId + '\'' +
        ", chatId='" + chatId + '\'' +
        ", otherUsername='" + otherUsername + '\'' +
        ", partCount=" + partCount +
        ", partNumber=" + partNumber +
        ", fileData=" + Arrays.toString(fileData) +
        '}';
  }
}
