package org.reminstant.secretalk.server.dto.nats;

import lombok.Getter;
import org.reminstant.secretalk.server.dto.common.ChatConfiguration;

import java.util.Objects;
import java.util.UUID;

@Getter
public class ChatConnectionRequestEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatConnectionRequest";

  private final String chatId;
  private final String requesterUsername;
  private final ChatConfiguration chatConfiguration;
  private final String publicKey;

  public ChatConnectionRequestEvent(String chatId, String requesterUsername,
                                    ChatConfiguration chatConfiguration, String publicKey) {
    this(UUID.randomUUID().toString(), chatId, requesterUsername, chatConfiguration, publicKey);
  }

  public ChatConnectionRequestEvent(String id, String chatId, String requesterUsername,
                                    ChatConfiguration chatConfiguration, String publicKey) {
    super(id);
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(requesterUsername, "requesterUsername cannot be null");
    Objects.requireNonNull(chatConfiguration, "configuration cannot be null");
    Objects.requireNonNull(publicKey, "publicKey cannot be null");
    this.chatId = chatId;
    this.requesterUsername = requesterUsername;
    this.chatConfiguration = chatConfiguration;
    this.publicKey = publicKey;
  }

  ChatConnectionRequestEvent() {
    this("", "", "", new ChatConfiguration(), "");
  }
}