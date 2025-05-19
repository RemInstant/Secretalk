package org.reminstant.cryptomessengerclient.repository;

import org.reminstant.cryptomessengerclient.model.Message;
import org.reminstant.cryptomessengerclient.model.Chat;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.List;

@Repository
public class LocalStorage {

  public List<Chat> getSecretChats() {
    // TODO:
    return List.of(
        new Chat("1", "abobaChat", Chat.State.CONNECTED),
        new Chat("2", "meowChat", Chat.State.AWAITING),
        new Chat("3", "meowChat", Chat.State.PENDING),
        new Chat("4", "meowChat", Chat.State.DISCONNECTED),
        new Chat("5", "meowChat", Chat.State.DESERTED),
        new Chat("6", "meowChat", Chat.State.DESTROYED));
  }

  public List<Message> getMessages(String chatId) {
    if (chatId.equals("3")) {
      return List.of(
          new Message("1", "TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT", "aboba", true),
          new Message("2", "TEXT TEXT TEXT", "aboba", Path.of("/home/remi/Code/abc.png"), true),
          new Message("3", "TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT TEXT", "aboba", false),
          new Message("4", "TEXT TEXT TEXT", "aboba", false),
          new Message("5", "TEXT TEXT TEXT", "aboba", true)
      );
    }
    if (chatId.equals("5")) {
      return List.of(new Message("6", "PLEASE DO NOT DELETE MMEEEEE", "deserted chat", false));
    }
    return List.of();
  }
}
