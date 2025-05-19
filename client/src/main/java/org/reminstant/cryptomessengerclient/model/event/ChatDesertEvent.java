package org.reminstant.cryptomessengerclient.model.event;

import lombok.Getter;

import java.util.Objects;

@Getter
public class ChatDesertEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatDesert";

  private final String chatId;
  private final String senderUsername;

  ChatDesertEvent(String id, String chatId, String senderUsername) {
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