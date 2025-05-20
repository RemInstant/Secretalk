package org.reminstant.secretalk.server.dto.nats;

import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
public class ChatFileEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatFile";

  private final String messageId;
  private final String chatId;
  private final String senderUsername;
  private final long partCount;
  private final long partNumber;
  private final byte[] fileData;

  public ChatFileEvent(String messageId, String chatId, String senderUsername,
                       long partCount, long partNumber, byte[] fileData) {
    this(UUID.randomUUID().toString(), messageId, chatId, senderUsername, partCount, partNumber, fileData);
  }

  public ChatFileEvent(String id, String messageId, String chatId, String senderUsername,
                       long partCount, long partNumber, byte[] fileData) {
    super(id);
    Objects.requireNonNull(messageId, "messageId cannot be null");
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(senderUsername, "senderUsername cannot be null");
    Objects.requireNonNull(fileData, "messageData cannot be null");
    this.messageId = messageId;
    this.chatId = chatId;
    this.senderUsername = senderUsername;
    this.partCount = partCount;
    this.partNumber = partNumber;
    this.fileData = fileData;
  }

  ChatFileEvent() {
    this("", "", "", "", 0, 0, new byte[0]);
  }
}