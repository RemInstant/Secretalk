package org.reminstant.cryptomessengerserver.dto.nats;

import lombok.Getter;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Getter
public class ChatConnectionBreakEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatConnectionBreak";

  private final String chatId;
  private final String senderUsername;

  public ChatConnectionBreakEvent(String chatId, String senderUsername) {
    this(UUID.randomUUID().toString(), chatId, senderUsername);
  }

  public ChatConnectionBreakEvent(String id, String chatId, String senderUsername) {
    super(id);
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(senderUsername, "senderUsername cannot be null");
    this.chatId = chatId;
    this.senderUsername = senderUsername;
  }

  public ChatConnectionBreakEvent(Map<String, String> data) {
    this(data.getOrDefault("id", null),
        data.getOrDefault("chatId", null),
        data.getOrDefault("senderUsername", null));
  }
}