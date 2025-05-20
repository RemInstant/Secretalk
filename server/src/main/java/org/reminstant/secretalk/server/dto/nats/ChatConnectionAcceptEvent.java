package org.reminstant.secretalk.server.dto.nats;

import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
public class ChatConnectionAcceptEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatConnectionAccept";

  private final String chatId;
  private final String acceptorUsername;
  private final String publicKey;

  public ChatConnectionAcceptEvent(String chatId, String acceptorUsername, String publicKey) {
    this(UUID.randomUUID().toString(), chatId, acceptorUsername, publicKey);
  }

  public ChatConnectionAcceptEvent(String id, String chatId, String acceptorUsername, String publicKey) {
    super(id);
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(acceptorUsername, "acceptorUsername cannot be null");
    Objects.requireNonNull(publicKey, "publicKey cannot be null");
    this.chatId = chatId;
    this.acceptorUsername = acceptorUsername;
    this.publicKey = publicKey;
  }

  ChatConnectionAcceptEvent() {
    this("", "", "", "");
  }
}