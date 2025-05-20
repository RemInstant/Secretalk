package org.reminstant.secretalk.server.dto.nats;

import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
public class ChatDesertEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatDesert";

  private final String chatId;
  private final String senderUsername;

  public ChatDesertEvent(String chatId, String senderUsername) {
    this(UUID.randomUUID().toString(), chatId, senderUsername);
  }

  public ChatDesertEvent(String id, String chatId, String senderUsername) {
    super(id);
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(senderUsername, "senderUsername cannot be null");
    this.chatId = chatId;
    this.senderUsername = senderUsername;
  }

  ChatDesertEvent() {
    this("", "", "");
  }
}