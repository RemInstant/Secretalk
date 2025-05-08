package org.reminstant.cryptomessengerclient.application;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.cryptomessengerclient.application.control.SecretChatEntry;
import org.reminstant.cryptomessengerclient.model.SecretChat;
import org.reminstant.cryptomessengerclient.repository.LocalStorage;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

@Slf4j
@Component
public class ChatManager {

  private final LocalStorage localStorage;

  private Pane chatHolder = null;
  private Label chatTitle = null;

  private final Map<SecretChat.State, Pane> stateBlocks;
  private final Map<String, SecretChatEntry> secretChatEntries;
  private final SimpleObjectProperty<SecretChatEntry> activeChat;


  public ChatManager(LocalStorage localStorage) {
    this.localStorage = localStorage;

    this.stateBlocks = new EnumMap<>(SecretChat.State.class);
    this.secretChatEntries = new ConcurrentHashMap<>();
    this.activeChat = new SimpleObjectProperty<>(null);

    activeChat.addListener((_, oldActiveChat, newActiveChat) -> {
      if (oldActiveChat != null) {
        oldActiveChat.setChatActive(false);
        stateBlocks.get(oldActiveChat.getSecretChat().getState()).setVisible(false);
      }
      if (newActiveChat != null) {
        newActiveChat.setChatActive(true);
        chatTitle.setText(newActiveChat.getSecretChat().getTitle());
        stateBlocks.get(newActiveChat.getSecretChat().getState()).setVisible(true);
      }
    });
  }

  public void initObjects(Pane chatHolder,
                          Label chatTitle,
                          StackPane chatStateBlockHolder) {
    Objects.requireNonNull(chatHolder, "chatHolder cannot be null");
    Objects.requireNonNull(chatTitle, "chatTitle cannot be null");
    this.chatHolder = chatHolder;
    this.chatTitle = chatTitle;

    chatStateBlockHolder.getChildren().forEach(node -> {
      if (node instanceof Pane pane) {
        String stateName = pane.getId().replace("StateBlock", "").toUpperCase();
        SecretChat.State state = SecretChat.State.valueOf(stateName);
        stateBlocks.put(state, pane);
      }
    });
    stateBlocks.values().forEach(holder -> holder.setVisible(false));

    if (stateBlocks.size() != SecretChat.State.values().length) {
      throw new IllegalArgumentException("Some state blocks was not found");
    }
  }

  public void initBehaviour(Runnable onChatOpening,
                            Runnable onChatClosing,
                            ToIntFunction<SecretChat> onChatRequest,
                            ToIntFunction<SecretChat> onChatAccept,
                            ToIntFunction<SecretChat> onChatDisconnect) {
    Button acceptChatButton = findButtonByState(SecretChat.State.AWAITING);
    Button disconnectChatButton = findButtonByState(SecretChat.State.CONNECTED);
    Button requestChatButton = findButtonByState(SecretChat.State.DISCONNECTED);

    Objects.requireNonNull(acceptChatButton, "Accept button not found in awaiting state block");
    Objects.requireNonNull(disconnectChatButton, "Disconnect button not found in connected state block");
    Objects.requireNonNull(requestChatButton, "Request button not found in disconnected state block");

    if (onChatOpening != null || onChatClosing != null) {
      activeChat.addListener((_, oldActiveChat, newActiveChat) -> {
        if (oldActiveChat == null && onChatOpening != null) {
          onChatOpening.run();
        }
        if (newActiveChat == null && onChatClosing != null) {
          onChatClosing.run();
        }
      });
    }

    Map<Button, Runnable> buttonHandlers = Map.of(
        acceptChatButton,
        constructHandlerForStateButton(onChatAccept, "FAILED TO ACCEPT"),
        disconnectChatButton,
        constructHandlerForStateButton(onChatDisconnect, "FAILED TO DISCONNECT"),
        requestChatButton,
        constructHandlerForStateButton(onChatRequest, "FAILED TO REQUEST")
    );

    for (var entry : buttonHandlers.entrySet()) {
      Button button = entry.getKey();
      Runnable handler = entry.getValue();
      button.setOnMouseClicked(e -> {
        if (e.getButton().equals(MouseButton.PRIMARY)) {
          handler.run();
        }
      });
    }
  }

  public void reset() {
    chatHolder.getChildren().clear();
    secretChatEntries.clear();
    activeChat.set(null);
  }

  public void loadChats() {
    throwIfUninitialised();
    localStorage.getSecretChats().forEach(this::createOrReconnect);
  }

  public String getActiveChatId() {
    SecretChatEntry chatEntry = activeChat.get();
    return chatEntry != null ? chatEntry.getSecretChat().getId() : null;
  }

  public String getActiveChatOtherUsername() {
    SecretChatEntry chatEntry = activeChat.get();
    return chatEntry != null ? chatEntry.getSecretChat().getTitle() : null;
  }

  public boolean isActiveChatAboutToDelete() {
    SecretChatEntry chatEntry = activeChat.get();
    return chatEntry != null && (
        chatEntry.getSecretChat().getState().equals(SecretChat.State.DESERTED) ||
        chatEntry.getSecretChat().getState().equals(SecretChat.State.DESTROYED));
  }

  public void createOrReconnectOnRequesterSide(String chatId, String companionUsername, BigInteger privateKey) {
    createOrReconnect(new SecretChat(chatId, companionUsername, SecretChat.State.PENDING, privateKey));
  }

  public void createOrReconnectOnAcceptorSide(String chatId, String companionUsername, BigInteger publicKey) {
    createOrReconnect(new SecretChat(chatId, companionUsername, SecretChat.State.AWAITING, publicKey));
  }

  public void acceptChat(String chatId, String otherUsername, UnaryOperator<BigInteger> sessionKeyGenerator) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to accept non-existent chat");
      return;
    }
    if (!chatEntry.getSecretChat().getTitle().equals(otherUsername)) {
      log.error("Tried to accept someone else's chat (got notification from {})", otherUsername);
      return;
    }

    chatEntry.setConnectedState(sessionKeyGenerator.apply(chatEntry.getSecretChat().getKey()));
    log.info("Established connection to chat: {}", chatEntry.getSecretChat());
  }

  public void disconnectChat(String chatId, String otherUsername) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to disconnect from non-existent chat");
      return;
    }
    if (!chatEntry.getSecretChat().getTitle().equals(otherUsername)) {
      log.error("Tried to disconnect from someone else's chat (got notification from {})", otherUsername);
      return;
    }

    chatEntry.setDisconnectedState();
    log.info("Broke connection to chat: {}", chatEntry.getSecretChat());
  }

  public void desertChat(String chatId, String otherUsername) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to make non-existent chat deserted");
      return;
    }
    if (!chatEntry.getSecretChat().getTitle().equals(otherUsername)) {
      log.error("Tried to make someone else's chat deserted (got notification from {})", otherUsername);
      return;
    }

    chatEntry.setDesertedState();
    log.info("Chat turned deserted: {}", chatEntry.getSecretChat());
  }

  public void destroyChat(String chatId, String otherUsername) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to destroy non-existent chat");
      return;
    }
    if (!chatEntry.getSecretChat().getTitle().equals(otherUsername)) {
      log.error("Tried to destroy someone else's chat (got notification from {})", otherUsername);
      return;
    }

    chatEntry.setDestroyedState();
    log.info("Destroyed chat: {}", chatEntry.getSecretChat());
  }

  public void deleteChat(String chatId) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to delete non-existent chat");
      return;
    }

    activeChat.set(null);
    String idToDelete = chatEntry.getSecretChat().getId();

    Runnable runnable = () -> chatHolder.getChildren().removeIf(node ->
        node instanceof SecretChatEntry entry &&
        entry.getSecretChat().getId().equals(idToDelete));

    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }

    secretChatEntries.remove(chatEntry.getSecretChat().getId());
    log.info("Deleted chat: {}", chatEntry.getSecretChat());
  }

  

  private void createOrReconnect(SecretChat chat) {
    throwIfUninitialised();
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chat.getId(), null);
    if (chatEntry != null) {
      switch (chat.getState()) {
        case PENDING -> chatEntry.setPendingState(chat.getKey());
        case AWAITING -> chatEntry.setAwaitingState(chat.getKey());
        default -> log.error("Got illegal chat state for creation or reconnecting");
      }
      return;
    }

    chatEntry = new SecretChatEntry(chat);
    secretChatEntries.put(chat.getId(), chatEntry);

    chatEntry.getStateProperty().addListener((observableValue, oldState, newState) -> {
      // TODO: research memory leaks
      if (activeChat.get() != null && observableValue == activeChat.get().getStateProperty()) {
        stateBlocks.get(oldState).setVisible(false);
        stateBlocks.get(newState).setVisible(true);
      }
    });

    SecretChatEntry finalChatEntry = chatEntry;
    Runnable runnable = () -> {
      chatHolder.getChildren().add(finalChatEntry);
      finalChatEntry.setOnMouseClicked(this::onSecretChatEntryClicked);
      log.debug("Created chat: {}", chat);
    };

    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }

  private void onSecretChatEntryClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY) &&
        e.getSource() instanceof SecretChatEntry entry) {
      activeChat.set(entry);
    }
  }

  private Button findButtonByState(SecretChat.State state) {
    return stateBlocks.get(state).getChildren().stream()
        .map(node -> node instanceof Button button ? button : null)
        .filter(Objects::nonNull)
        .findAny().orElse(null);
  }

  private Runnable constructHandlerForStateButton(ToIntFunction<SecretChat> rawHandler, String errorMessage) {
    if (rawHandler == null) {
      return () -> {};
    }
    return () -> {
      int status = rawHandler.applyAsInt(activeChat.get().getSecretChat());
      if (status != 200) {
        log.error("{}: code {}", errorMessage, status);
      }
    };
  }

  private void throwIfUninitialised() {
    if (chatHolder == null) {
      throw new IllegalStateException("ChatManager is uninitialised");
    }
  }
}