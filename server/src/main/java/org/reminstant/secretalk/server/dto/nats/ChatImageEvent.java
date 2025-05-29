package org.reminstant.secretalk.server.dto.nats;

import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
public class ChatImageEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatImage";

  private final String messageId;
  private final String chatId;
  private final String senderUsername;
  private final String fileName;
  private final byte[] imageData;

  public ChatImageEvent(String messageId, String chatId, String senderUsername,
                        String fileName, byte[] imageData) {
    this(UUID.randomUUID().toString(), messageId, chatId, senderUsername, fileName, imageData);
  }

  public ChatImageEvent(String id, String messageId, String chatId, String senderUsername,
                        String fileName, byte[] imageData) {
    super(id);
    Objects.requireNonNull(messageId, "messageId cannot be null");
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(senderUsername, "senderUsername cannot be null");
    Objects.requireNonNull(fileName, "fileName cannot be null");
    Objects.requireNonNull(imageData, "imageData cannot be null");
    this.messageId = messageId;
    this.chatId = chatId;
    this.senderUsername = senderUsername;
    this.fileName = fileName;
    this.imageData = imageData;
  }

  ChatImageEvent() {
    this("", "", "", "", "", new byte[0]);
  }
}