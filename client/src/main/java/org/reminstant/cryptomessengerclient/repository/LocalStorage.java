package org.reminstant.cryptomessengerclient.repository;

import org.reminstant.cryptomessengerclient.application.control.MessageEntry;
import org.reminstant.cryptomessengerclient.model.Message;
import org.reminstant.cryptomessengerclient.model.SecretChat;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Repository
public class LocalStorage {

  public List<SecretChat> getSecretChats() {
    // TODO:
    return List.of(
        new SecretChat("1", "abobaChat", SecretChat.State.CONNECTED),
        new SecretChat("2", "meowChat", SecretChat.State.AWAITING),
        new SecretChat("3", "meowChat", SecretChat.State.PENDING),
        new SecretChat("4", "meowChat", SecretChat.State.DISCONNECTED),
        new SecretChat("5", "meowChat", SecretChat.State.DESERTED),
        new SecretChat("6", "meowChat", SecretChat.State.DESTROYED));
  }

  public List<Message> getMessages(String chatId) {
    if (chatId.equals("3")) {
      return List.of(
          new Message("TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT", "aboba", true),
          new Message("TEXT TEXT TEXT", "aboba", true),
          new Message("TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT", "aboba", false),
          new Message("TEXT TEXT TEXT", "aboba", false),
          new Message("TEXT TEXT TEXT", "aboba", true)
      );
    }
    if (chatId.equals("5")) {
      return List.of(new Message("PLEASE DO NOT DELETE MMEEEEE", "deserted chat", false));
    }
    return List.of();
  }
}
