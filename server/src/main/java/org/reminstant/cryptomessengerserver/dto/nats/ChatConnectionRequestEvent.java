package org.reminstant.cryptomessengerserver.dto.nats;

import lombok.Getter;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Getter
public class ChatConnectionRequestEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatConnectionRequest";

  private final String chatId;
  private final String requesterUsername;
  private final String publicKey;

  public ChatConnectionRequestEvent(String chatId, String requesterUsername, String publicKey) {
    this(UUID.randomUUID().toString(), chatId, requesterUsername, publicKey);
  }

  public ChatConnectionRequestEvent(String id, String chatId, String requesterUsername, String publicKey) {
    super(id);
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(requesterUsername, "requesterUsername cannot be null");
    Objects.requireNonNull(publicKey, "publicKey cannot be null");
    this.chatId = chatId;
    this.requesterUsername = requesterUsername;
    this.publicKey = publicKey;
  }

  public ChatConnectionRequestEvent(Map<String, String> data) {
    this(data.getOrDefault("id", null),
        data.getOrDefault("chatId", null),
        data.getOrDefault("requesterUsername", null),
        data.getOrDefault("publicKey", null));
  }
}