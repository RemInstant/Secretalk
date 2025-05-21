package org.reminstant.secretalk.client.application;

import javafx.beans.property.SimpleObjectProperty;
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
import org.reminstant.cryptography.context.CryptoProgress;
import org.reminstant.cryptography.context.SymmetricCryptoContext;
import org.reminstant.secretalk.client.application.control.MessageEntry;
import org.reminstant.secretalk.client.application.control.SecretChatEntry;
import org.reminstant.secretalk.client.exception.*;
import org.reminstant.secretalk.client.model.Message;
import org.reminstant.secretalk.client.model.Chat;
import org.reminstant.secretalk.client.repository.LocalStorage;
import org.reminstant.secretalk.client.util.FxUtil;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
  private final Map<String, MessageEntry> messageEntries;
  private final SimpleObjectProperty<SecretChatEntry> activeChat;
  private final Map<String, VBox> messageHolders;
  private final Map<String, SymmetricCryptoContext> cryptoContexts;


  public ChatManager(LocalStorage localStorage) {
    this.localStorage = localStorage;

    this.stateBlocks = new EnumMap<>(Chat.State.class);
    this.secretChatEntries = new ConcurrentHashMap<>();
    this.messageEntries = new ConcurrentHashMap<>();
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
                            Runnable onChatChanging) {
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
  }

  public void loadChats() throws LocalStorageReadException {
    throwIfUninitialised();
    List<Chat> chats = localStorage.getChats();
    for (Chat chat : chats) {
      List<Message> messages = localStorage.getMessages(chat.getId());
      try {
        createOrReconnect(chat, true);
        insertMessages(chat.getId(), messages, true);
      } catch (LocalStorageWriteException ex) {
        throw new IllegalStateException("Should not be thrown (bug)", ex);
      }
    }
  }

  public void reset() {
    throwIfUninitialised();
    chatHolder.getChildren().clear();
    secretChatEntries.clear();
    messageEntries.clear();
    messageHolders.clear();
    cryptoContexts.clear();
    activeChat.set(null);
  }


  public String getActiveChatId() {
    throwIfUninitialised();
    SecretChatEntry chatEntry = activeChat.get();
    return chatEntry != null ? chatEntry.getChat().getId() : null;
  }

  public String getChatOtherUsername(String chatId) {
    if (chatId == null) {
      return null;
    }
    return secretChatEntries.get(chatId).getChat().getOtherUsername();
  }

  public Chat.Configuration getChatConfiguration(String chatId) {
    if (chatId == null) {
      return null;
    }
    return secretChatEntries.get(chatId).getChat().getConfiguration();
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


  public void createOrReconnectOnRequesterSide(String chatId, String otherUsername, Chat.Configuration config,
                                               BigInteger privateKey) throws LocalStorageWriteException {
    createOrReconnect(new Chat(chatId, otherUsername, config, Chat.State.PENDING, privateKey), false);
  }

  public void createOrReconnectOnAcceptorSide(String chatId, String otherUsername, Chat.Configuration config,
                                              BigInteger privateKey) throws LocalStorageWriteException {
    createOrReconnect(new Chat(chatId, otherUsername, config, Chat.State.AWAITING, privateKey), false);
  }

  public void acceptChat(String chatId, String otherUsername,
                         UnaryOperator<BigInteger> sessionKeyGenerator) throws LocalStorageWriteException {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry, otherUsername);
    BigInteger sessionKey = sessionKeyGenerator.apply(chatEntry.getChat().getKey());
    localStorage.updateChatConfig(chatId, Chat.State.CONNECTED, sessionKey);
    FxUtil.runOnFxThread(() -> {
      chatEntry.setConnectedState(sessionKey);
      log.info("Established connection to chat: {}", chatEntry.getChat());
    });
  }

  public void disconnectChat(String chatId, String otherUsername) throws LocalStorageWriteException {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry, otherUsername);
    localStorage.updateChatConfig(chatId, Chat.State.DISCONNECTED, null);
    FxUtil.runOnFxThread(() -> {
      chatEntry.setDisconnectedState();
      log.info("Broke connection to chat: {}", chatEntry.getChat());
    });
  }

  public void desertChat(String chatId, String otherUsername) throws LocalStorageWriteException {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry, otherUsername);
    localStorage.updateChatConfig(chatId, Chat.State.DESERTED, null);
    FxUtil.runOnFxThread(() -> {
      chatEntry.setDesertedState();
      log.info("Chat turned deserted: {}", chatEntry.getChat());
    });
  }

  public void destroyChat(String chatId, String otherUsername)
      throws LocalStorageWriteException, LocalStorageDeletionException {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry, otherUsername);
    localStorage.updateChatConfig(chatId, Chat.State.DESTROYED, null);
    localStorage.clearChat(chatId);
    FxUtil.runOnFxThread(() -> {
      messageHolders.get(chatId).getChildren().clear();
      chatEntry.setDestroyedState();
      log.info("Destroyed chat: {}", chatEntry.getChat());
    });
  }

  public void deleteChat(String chatId) throws LocalStorageDeletionException {
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry);

    localStorage.deleteChat(chatId);
    FxUtil.runOnFxThread(() -> {
      activeChat.set(null);
      chatHolder.getChildren().removeIf(node -> node instanceof SecretChatEntry entry &&
          entry.getChat().getId().equals(chatId));
    });

    secretChatEntries.remove(chatEntry.getChat().getId());
    log.info("Deleted chat: {}", chatEntry.getChat());
  }

  public void insertMessage(String chatId, Message message,
                            boolean isLoadedFromDisk) throws LocalStorageWriteException {
    throwIfIllegalRequest(chatId);
    if (!isLoadedFromDisk) {
      localStorage.saveMessage(chatId, message);
    }

    MessageEntry messageEntry = new MessageEntry(message);
    VBox messageHolder = messageHolders.get(chatId);

    messageEntries.put(message.getId(), messageEntry);
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
  }

  public void insertMessages(String chatId, Collection<Message> messages,
                             boolean isLoadedFromDisk) throws LocalStorageWriteException {
    throwIfIllegalRequest(chatId);
    for (Message msg : messages) {
      insertMessage(chatId, msg, isLoadedFromDisk);
    }
  }

  public void startMessageEncryption(String chatId, String messageId)
      throws LocalStorageWriteException {
    localStorage.updateMessageState(messageId, chatId, Message.State.ENCRYPTING);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startEncryption());
  }

  public void startMessageEncryption(String chatId, String messageId, CryptoProgress<?> progress)
      throws LocalStorageWriteException {
    localStorage.updateMessageState(messageId, chatId, Message.State.ENCRYPTING);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startEncryption(progress));
  }

  public void startMessageTransmission(String chatId, String messageId)
      throws LocalStorageWriteException {
    localStorage.updateMessageState(messageId, chatId, Message.State.TRANSMITTING);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startTransmission());
  }

  public void startMessageTransmission(String chatId, String messageId, HttpMultipartProgress progress)
      throws LocalStorageWriteException {
    localStorage.updateMessageState(messageId, chatId, Message.State.TRANSMITTING);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startTransmission(progress));
  }

  public void startMessageDecryption(String chatId, String messageId)
      throws LocalStorageWriteException {
    localStorage.updateMessageState(messageId, chatId, Message.State.DECRYPTING);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startDecryption());
  }

  public void startMessageDecryption(String chatId, String messageId, CryptoProgress<?> progress)
      throws LocalStorageWriteException {
    localStorage.updateMessageState(messageId, chatId, Message.State.DECRYPTING);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startDecryption(progress));
  }

  public void failMessage(String chatId, String messageId)
      throws LocalStorageWriteException {
    localStorage.updateMessageState(messageId, chatId, Message.State.FAILED);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).fail());
  }

  public void cancelMessage(String chatId, String messageId)
      throws LocalStorageWriteException {
    localStorage.updateMessageState(messageId, chatId, Message.State.CANCELLED);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).cancel());
  }

  public void completeMessage(String chatId, String messageId)
      throws LocalStorageWriteException {
    localStorage.updateMessageState(messageId, chatId, Message.State.SENT);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).complete());
  }



  private void createOrReconnect(Chat chat, boolean isLoadedFromDisk) throws LocalStorageWriteException {
    throwIfUninitialised();
    if (!isLoadedFromDisk) {
      localStorage.saveChatConfig(chat);
    }
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chat.getId(), null);
    if (chatEntry != null) {
      switch (chat.getState()) {
        case PENDING -> chatEntry.setPendingState(chat.getKey());
        case AWAITING -> chatEntry.setAwaitingState(chat.getKey());
        default -> throw new IllegalChatManagerRequest("Illegal chat state for creation or reconnection");
      }
      localStorage.updateChatConfig(chat.getId(), chat.getState(), chat.getKey());
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

  private void throwIfUninitialised() {
    if (chatHolder == null) {
      throw new ModuleUninitialisedStateException("ChatManager is uninitialised");
    }
  }

  private void throwIfIllegalRequest(String chatId) {
    throwIfIllegalRequest(secretChatEntries.getOrDefault(chatId, null));
  }

  private void throwIfIllegalRequest(SecretChatEntry chatEntry) {
    if (chatEntry == null) {
      throw new IllegalChatManagerRequest("Illegal chat request (chatEntry is null)");
    }
  }

  private void throwIfIllegalRequest(SecretChatEntry chatEntry, String otherUsername) {
    throwIfIllegalRequest(chatEntry);
    String expectedOtherUsername = chatEntry.getChat().getOtherUsername();
    if (!expectedOtherUsername.equals(otherUsername)) {
      throw new IllegalChatManagerRequest(
          "Illegal chat request (expected otherUsername '%s' but got '%s'"
              .formatted(expectedOtherUsername, otherUsername));
    }
  }
}