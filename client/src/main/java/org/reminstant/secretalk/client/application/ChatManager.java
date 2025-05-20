package org.reminstant.secretalk.client.application;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.concurrent.ConcurrentUtil;
import org.reminstant.cryptography.CryptoProvider;
import org.reminstant.cryptography.context.SymmetricCryptoContext;
import org.reminstant.secretalk.client.application.control.MessageEntry;
import org.reminstant.secretalk.client.application.control.SecretChatEntry;
import org.reminstant.secretalk.client.model.Message;
import org.reminstant.secretalk.client.model.Chat;
import org.reminstant.secretalk.client.repository.LocalStorage;
import org.reminstant.secretalk.client.util.FxUtil;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@Slf4j
@Component
public class ChatManager {

  private final LocalStorage localStorage;

  private Pane chatHolder = null;
  private Label chatTitle = null;
  private ScrollPane messageHolderWrapper = null;

  private final Map<Chat.State, Pane> stateBlocks;
  private final Map<String, SecretChatEntry> secretChatEntries;
  private final SimpleObjectProperty<SecretChatEntry> activeChat;
  private final Map<String, VBox> messageHolders;
  private final Map<String, SymmetricCryptoContext> cryptoContexts;


  public ChatManager(LocalStorage localStorage) {
    this.localStorage = localStorage;

    this.stateBlocks = new EnumMap<>(Chat.State.class);
    this.secretChatEntries = new ConcurrentHashMap<>();
    this.activeChat = new SimpleObjectProperty<>(null);
    this.messageHolders = new ConcurrentHashMap<>();
    this.cryptoContexts = new ConcurrentHashMap<>();

    activeChat.addListener((_, oldActiveChat, newActiveChat) -> {
      if (oldActiveChat != null) {
        oldActiveChat.setChatActive(false);
        stateBlocks.get(oldActiveChat.getChat().getState()).setVisible(false);
      }
      if (newActiveChat != null) {
        newActiveChat.setChatActive(true);
        chatTitle.setText(newActiveChat.getChat().getTitle());
        stateBlocks.get(newActiveChat.getChat().getState()).setVisible(true);
        messageHolderWrapper.setContent(messageHolders.get(newActiveChat.getChat().getId()));
      } else {
        messageHolderWrapper.setContent(null);
      }
    });
  }

  public void initObjects(Pane chatHolder,
                          Label chatTitle,
                          StackPane chatStateBlockHolder,
                          ScrollPane messageHolderWrapper) {
    Objects.requireNonNull(chatHolder, "chatHolder cannot be null");
    Objects.requireNonNull(chatTitle, "chatTitle cannot be null");
    this.chatHolder = chatHolder;
    this.chatTitle = chatTitle;
    this.messageHolderWrapper = messageHolderWrapper;

    chatStateBlockHolder.getChildren().forEach(node -> {
      if (node instanceof Pane pane) {
        String stateName = pane.getId().replace("StateBlock", "").toUpperCase();
        Chat.State state = Chat.State.valueOf(stateName);
        stateBlocks.put(state, pane);
      }
    });
    stateBlocks.values().forEach(holder -> holder.setVisible(false));

    if (stateBlocks.size() != Chat.State.values().length) {
      throw new IllegalArgumentException("Some state blocks was not found");
    }
  }

  public void initBehaviour(Runnable onChatOpening,
                            Runnable onChatClosing,
                            Runnable onChatChanging,
                            Function<Chat, ChainableFuture<Integer>> onChatRequest,
                            Function<Chat, ChainableFuture<Integer>> onChatAccept,
                            Function<Chat, ChainableFuture<Integer>> onChatDisconnect) {
    Button acceptChatButton = findButtonByState(Chat.State.AWAITING);
    Button disconnectChatButton = findButtonByState(Chat.State.CONNECTED);
    Button requestChatButton = findButtonByState(Chat.State.DISCONNECTED);

    Objects.requireNonNull(acceptChatButton, "Accept button not found in awaiting state block");
    Objects.requireNonNull(disconnectChatButton, "Disconnect button not found in connected state block");
    Objects.requireNonNull(requestChatButton, "Request button not found in disconnected state block");

    activeChat.addListener((_, oldActiveChat, newActiveChat) -> {
      if (onChatOpening != null && oldActiveChat == null) {
        onChatOpening.run();
      }
      if (onChatClosing != null && newActiveChat == null) {
        onChatClosing.run();
      }
      if (onChatChanging != null) {
        onChatChanging.run();
      }
    });

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
    messageHolders.keySet().forEach(chatId -> insertMessages(chatId, localStorage.getMessages(chatId)));
  }


  public String getActiveChatId() {
    SecretChatEntry chatEntry = activeChat.get();
    return chatEntry != null ? chatEntry.getChat().getId() : null;
  }

  public String getChatOtherUsername(String chatId) {
    if (chatId == null) {
      return null;
    }
    return secretChatEntries.get(chatId).getChat().getOtherUsername();
  }

  public boolean isChatAboutToDelete(String chatId) {
    SecretChatEntry chatEntry = secretChatEntries.get(chatId);
    return chatEntry != null && (
        chatEntry.getChat().getState().equals(Chat.State.DESERTED) ||
        chatEntry.getChat().getState().equals(Chat.State.DESTROYED));
  }

  public SymmetricCryptoContext getChatCryptoContext(String chatId) {
    SecretChatEntry chatEntry = secretChatEntries.get(chatId);
    if (chatEntry == null) {
      return null;
    }
    return cryptoContexts.computeIfAbsent(chatId, _ -> {
      Chat chat = chatEntry.getChat();
      byte[] key = chat.getKey().toByteArray();
      BigInteger randomDelta = new BigInteger(1, chat.getRandomDelta());
      return CryptoProvider.constructContext(chat.getCryptoSystemName(), key,
          chat.getCipherMode(), chat.getPaddingMode(), chat.getInitVector(), randomDelta);
    });
  }


  public void createOrReconnectOnRequesterSide(String chatId, String otherUsername,
                                               Chat.Configuration config, BigInteger privateKey) {
    createOrReconnect(new Chat(chatId, otherUsername, config, Chat.State.PENDING, privateKey));
  }

  public void createOrReconnectOnAcceptorSide(String chatId, String otherUsername,
                                              Chat.Configuration config, BigInteger privateKey) {
    createOrReconnect(new Chat(chatId, otherUsername, config, Chat.State.AWAITING, privateKey));
  }

  public void acceptChat(String chatId, String otherUsername, UnaryOperator<BigInteger> sessionKeyGenerator) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to accept non-existent chat");
      return;
    }
    if (!chatEntry.getChat().getOtherUsername().equals(otherUsername)) {
      log.error("Tried to accept someone else's chat (got notification from {})", otherUsername);
      return;
    }

    FxUtil.runOnFxThread(() -> {
      chatEntry.setConnectedState(sessionKeyGenerator.apply(chatEntry.getChat().getKey()));
      log.info("Established connection to chat: {}", chatEntry.getChat());
    });
  }

  public void disconnectChat(String chatId, String otherUsername) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to disconnect from non-existent chat");
      return;
    }
    if (!chatEntry.getChat().getOtherUsername().equals(otherUsername)) { // TODO: move it to appManager?
      log.error("Tried to disconnect from someone else's chat (got notification from {})", otherUsername);
      return;
    }

    FxUtil.runOnFxThread(() -> {
      chatEntry.setDisconnectedState();
      log.info("Broke connection to chat: {}", chatEntry.getChat());
    });
  }

  public void desertChat(String chatId, String otherUsername) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to make non-existent chat deserted");
      return;
    }
    if (!chatEntry.getChat().getOtherUsername().equals(otherUsername)) {
      log.error("Tried to make someone else's chat deserted (got notification from {})", otherUsername);
      return;
    }

    FxUtil.runOnFxThread(() -> {
      chatEntry.setDesertedState();
      log.info("Chat turned deserted: {}", chatEntry.getChat());
    });
  }

  public void destroyChat(String chatId, String otherUsername) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to destroy non-existent chat");
      return;
    }
    if (!chatEntry.getChat().getOtherUsername().equals(otherUsername)) {
      log.error("Tried to destroy someone else's chat (got notification from {})", otherUsername);
      return;
    }

    FxUtil.runOnFxThread(() -> {
      chatEntry.setDestroyedState();
      log.info("Destroyed chat: {}", chatEntry.getChat());
    });
  }

  public void deleteChat(String chatId) {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    if (chatEntry == null) {
      log.error("Tried to delete non-existent chat");
      return;
    }

    String idToDelete = chatEntry.getChat().getId();
    FxUtil.runOnFxThread(() -> {
      activeChat.set(null);
      chatHolder.getChildren().removeIf(node -> node instanceof SecretChatEntry entry &&
          entry.getChat().getId().equals(idToDelete));
    });

    secretChatEntries.remove(chatEntry.getChat().getId());
    log.info("Deleted chat: {}", chatEntry.getChat());
  }

  public MessageEntry insertMessage(String chatId, Message message) {
    if (!secretChatEntries.containsKey(chatId)) {
      log.error("Tried to insert message into non-existent chat");
      return null;
    }

    MessageEntry messageEntry = new MessageEntry(message);
    VBox messageHolder = messageHolders.get(chatId);
    FxUtil.runOnFxThread(() -> messageHolder.getChildren().add(messageEntry));
    ChainableFuture.runWeaklyAsync(() -> {
      ConcurrentUtil.sleepSafely(200);
      FxUtil.runOnFxThread(() -> {
        if (activeChat.get() != null && activeChat.get().getChat().getId().equals(chatId)) {
          messageHolderWrapper.setVvalue(1);
        }
      });
    });

    FxUtil.runOnFxThread(() -> messageEntry.setOnCancel(() ->
        messageHolder.getChildren().remove(messageEntry)));

    return messageEntry;
  }

  public void insertMessages(String chatId, Collection<Message> messages) {
    if (!secretChatEntries.containsKey(chatId)) {
      log.error("Tried to insert messages into non-existent chat");
      return;
    }
    messages.forEach(msg -> insertMessage(chatId, msg));
  }



  private void createOrReconnect(Chat chat) {
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
    VBox messageHolder = new VBox();

    secretChatEntries.put(chat.getId(), chatEntry);
    messageHolders.put(chat.getId(), messageHolder);

    chatEntry.getStateProperty().addListener((observableValue, oldState, newState) -> {
      // TODO: research memory leaks
      if (activeChat.get() != null && observableValue == activeChat.get().getStateProperty()) {
        stateBlocks.get(oldState).setVisible(false);
        stateBlocks.get(newState).setVisible(true);
      }
    });

    SecretChatEntry finalChatEntry = chatEntry;
    FxUtil.runOnFxThread(() -> {
      chatHolder.getChildren().add(finalChatEntry);
      finalChatEntry.setOnMouseClicked(this::onSecretChatEntryClicked);
      messageHolder.getStyleClass().add("messageHolder");
      messageHolder.minHeightProperty().bind(messageHolderWrapper.heightProperty().subtract(2));
      log.debug("Created chat: {}", chat);
    });
  }

  private void onSecretChatEntryClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY) &&
        e.getSource() instanceof SecretChatEntry entry) {
      activeChat.set(entry);
    }
  }

  private Button findButtonByState(Chat.State state) {
    return stateBlocks.get(state).getChildren().stream()
        .map(node -> node instanceof Button button ? button : null)
        .filter(Objects::nonNull)
        .findAny().orElse(null);
  }

  private Runnable constructHandlerForStateButton(Function<Chat, ChainableFuture<Integer>> rawHandler,
                                                  String errorMessage) {
    if (rawHandler == null) {
      return () -> {};
    }
    return () -> rawHandler.apply(activeChat.get().getChat())
        .thenWeaklyConsumeAsync(status -> FxUtil.runOnFxThread(() -> {
          // TODO: notification
          if (status != 200) {
            log.error("{}: code {}", errorMessage, status);
          }
        }));
  }

  private void throwIfUninitialised() {
    if (chatHolder == null) {
      throw new IllegalStateException("ChatManager is uninitialised");
    }
  }
}