package org.reminstant.cryptomessengerclient.model.event;

import lombok.Getter;
import org.reminstant.cryptomessengerclient.model.Chat;

import java.util.Objects;

@Getter
public class ChatConnectionRequestEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatConnectionRequest";

  private final String chatId;
  private final String requesterUsername;
  private final Chat.Configuration chatConfiguration;
  private final String publicKey;

  ChatConnectionRequestEvent(String id, String chatId, String requesterUsername,// NOSONAR
                             Chat.Configuration chatConfiguration, String publicKey) {
    super(id);
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(requesterUsername, "requesterUsername cannot be null");
    Objects.requireNonNull(chatConfiguration, "chatConfiguration cannot be null");
    Objects.requireNonNull(publicKey, "publicKey cannot be null");
    this.chatId = chatId;
    this.requesterUsername = requesterUsername;
    this.chatConfiguration = chatConfiguration;
    this.publicKey = publicKey;
  }

  ChatConnectionRequestEvent() {
    this("", "", "", new Chat.Configuration(), "");
  }
}