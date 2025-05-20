package org.reminstant.secretalk.server.dto.nats;

import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
public class ChatMessageEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatMessage";

  private final String messageId;
  private final String chatId;
  private final String senderUsername;
  private final byte[] messageData;
  private final String attachedFileName;

  public ChatMessageEvent(String messageId, String chatId, String senderUsername,
                          byte[] messageData, String attachedFileName) {
    this(UUID.randomUUID().toString(), messageId, chatId, senderUsername, messageData, attachedFileName);
  }

  public ChatMessageEvent(String id, String messageId, String chatId, String senderUsername,
                          byte[] messageData, String attachedFileName) {
    super(id);
    Objects.requireNonNull(messageId, "messageId cannot be null");
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(senderUsername, "senderUsername cannot be null");
    Objects.requireNonNull(messageData, "messageData cannot be null");
    this.messageId = messageId;
    this.chatId = chatId;
    this.senderUsername = senderUsername;
    this.messageData = messageData;
    this.attachedFileName = attachedFileName;
  }

  ChatMessageEvent() {
    this("", "", "", "", new byte[0], null);
  }
}