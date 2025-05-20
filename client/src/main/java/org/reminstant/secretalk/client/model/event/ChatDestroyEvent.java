package org.reminstant.secretalk.client.model.event;

import lombok.Getter;

import java.util.Objects;

@Getter
public class ChatDestroyEvent extends UserEvent {

  public static final String EVENT_NAME = "ChatDestroy";

  private final String chatId;
  private final String senderUsername;

  ChatDestroyEvent(String id, String chatId, String senderUsername) {
    super(id);
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(senderUsername, "senderUsername cannot be null");
    this.chatId = chatId;
    this.senderUsername = senderUsername;
  }

  ChatDestroyEvent() {
    this("", "", "");
  }
}