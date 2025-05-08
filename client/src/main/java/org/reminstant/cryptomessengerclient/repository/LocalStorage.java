package org.reminstant.cryptomessengerclient.repository;

import org.reminstant.cryptomessengerclient.model.SecretChat;
import org.springframework.stereotype.Repository;

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
}
