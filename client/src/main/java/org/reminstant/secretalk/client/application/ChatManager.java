package org.reminstant.secretalk.client.application;

import javafx.application.HostServices;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
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
import org.reminstant.concurrent.Progress;
import org.reminstant.cryptography.CryptoProvider;
import org.reminstant.cryptography.context.CryptoProgress;
import org.reminstant.cryptography.context.SymmetricCryptoContext;
import org.reminstant.secretalk.client.application.control.MessageEntry;
import org.reminstant.secretalk.client.application.control.SecretChatEntry;
import org.reminstant.secretalk.client.exception.*;
import org.reminstant.secretalk.client.model.Message;
import org.reminstant.secretalk.client.model.Chat;
import org.reminstant.secretalk.client.repository.LocalStorage;
import org.reminstant.secretalk.client.util.ClientStatus;
import org.reminstant.secretalk.client.util.FxUtil;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@Slf4j
@Component
public class ChatManager {

  private final LocalStorage localStorage;
  private final HostServices hostServices;

  private Pane chatHolder = null;
  private Label chatHint = null;
  private Label chatTitle = null;
  private ScrollPane messageHolderWrapper = null;
  private Function<Message, ChainableFuture<Integer>> onFileRequest = null;

  private final Map<Chat.State, Pane> stateBlocks;
  // Chat properties
  private final Map<String, SecretChatEntry> secretChatEntries;
  private final Map<String, VBox> messageHolders;
  private final Map<String, Set<String>> chatRequestingMessages;
  private final Map<String, SymmetricCryptoContext> cryptoContexts;
  private final SimpleObjectProperty<SecretChatEntry> activeChat;
  private final SimpleObjectProperty<Chat.State> activeChatState;
  // Message properties
  private final Map<String, MessageEntry> messageEntries;
  private final Map<String, MessageEntry> processingMessageEntries;


  public ChatManager(LocalStorage localStorage, HostServices hostServices) {
    this.localStorage = localStorage;
    this.hostServices = hostServices;

    this.stateBlocks = new EnumMap<>(Chat.State.class);
    this.secretChatEntries = new ConcurrentHashMap<>();
    this.messageHolders = new ConcurrentHashMap<>();
    this.chatRequestingMessages = new ConcurrentHashMap<>();
    this.cryptoContexts = new ConcurrentHashMap<>();
    this.activeChat = new SimpleObjectProperty<>(null);
    this.activeChatState = new SimpleObjectProperty<>(null);
    this.messageEntries = new ConcurrentHashMap<>();
    this.processingMessageEntries = new ConcurrentHashMap<>();

    activeChat.addListener((_, oldActiveChat, newActiveChat) -> {
      if (oldActiveChat != null) {
        oldActiveChat.setChatActive(false);
        stateBlocks.get(oldActiveChat.getChat().getState()).setVisible(false);
      }
      if (newActiveChat != null) {
        newActiveChat.setChatActive(true);
        Chat chat = newActiveChat.getChat();
        String title = chat.getTitle().equals(chat.getOtherUsername())
            ? chat.getTitle()
            : chat.getTitle() + " (" + chat.getOtherUsername() + ")";

        chatTitle.setText(title);
        stateBlocks.get(chat.getState()).setVisible(true);
        messageHolderWrapper.setContent(messageHolders.get(chat.getId()));
        activeChatState.set(chat.getState());
      } else {
        messageHolderWrapper.setContent(null);
        activeChatState.set(null);
      }

      messageHolderWrapper.setVvalue(1);
    });
  }


  public void initObjects(Pane chatHolder, Label chatHint, VBox chatBlock) throws ModuleInitialisationException {
    try {
      Parent chatHeader = FxUtil.getChildById(chatBlock, "chatHeader", Parent.class);
      StackPane chatStateBlockHolder = FxUtil.getChildById(chatBlock, "chatStateBlockHolder", StackPane.class);
      Parent chatFooter = FxUtil.getChildById(chatBlock, "chatFooter", Parent.class);

      this.chatHolder = chatHolder;
      this.chatHint = chatHint;
      this.chatTitle = FxUtil.getChildById(chatHeader, "chatTitle", Label.class);
      this.messageHolderWrapper = FxUtil.getChildById(chatBlock, "messageHolderWrapper", ScrollPane.class);

      chatStateBlockHolder.getChildren().forEach(node -> {
        if (node instanceof Pane pane) {
          String stateName = pane.getId().replace("StateBlock", "").toUpperCase();
          Chat.State state = Chat.State.valueOf(stateName);
          stateBlocks.put(state, pane);
        }
      });
      stateBlocks.values().forEach(holder -> holder.setVisible(false));

      if (stateBlocks.size() != Chat.State.values().length) {
        throw new FxControlNotFoundException("Some state blocks was not found");
      }

      activeChatState.addListener(_ -> chatFooter.getChildrenUnmodifiable().forEach(node -> {
        if (activeChatState.get() != null) {
          node.setVisible(activeChatState.get().equals(Chat.State.CONNECTED));
        }
      }));
    } catch (FxControlNotFoundException ex) {
      throw new ModuleInitialisationException("Failed to initialise because some control was not found", ex);
    }
  }

  public void initBehaviour(Runnable onChatOpening,
                            Runnable onChatClosing,
                            Runnable onChatChanging,
                            Function<Message, ChainableFuture<Integer>> onFileRequest) {
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
    this.onFileRequest = onFileRequest;
  }

  public void loadChats() {
    throwIfUninitialised();
    List<Chat> chats;
    try {
      chats = localStorage.getChats();
    } catch (LocalStorageReadException ex) {
      log.error("Failed to load chat data", ex);
      return;
    }
    for (Chat chat : chats) {
      try {
        List<Message> messages = localStorage.getMessages(chat.getId());
        messages = messages.stream().filter(msg -> !msg.getState().equals(Message.State.CANCELLED)).toList();

        for (Message message : messages) {
          Message.State state = message.getState();
          if (!state.equals(Message.State.SENT) && !state.equals(Message.State.REQUESTING)) {
            localStorage.updateMessageState(chat.getId(), message.getId(), Message.State.FAILED);
            message.setState(Message.State.FAILED);
          }
        }

        createOrReconnect(chat, true);
        insertMessages(chat.getId(), messages, true);
      } catch (LocalStorageReadException | LocalStorageWriteException ex) {
        log.error("Failed to load chat '{}'", chat.getTitle(), ex);
      }
    }
    FxUtil.runOnFxThread(this::updateChatHint);
  }

  public void reset() {
    throwIfUninitialised();
    processingMessageEntries.values().forEach(entry -> {
      Message.State state = entry.getMessage().getState();
      if (state != Message.State.REQUESTING && state != Message.State.SENT) {
        entry.fail();
      }
    });
    chatHolder.getChildren().clear();
    secretChatEntries.clear();
    messageHolders.clear();
    cryptoContexts.clear();
    activeChat.set(null);
    messageEntries.clear();
    processingMessageEntries.clear();
  }

  public boolean isInitialised() {
    return chatHolder != null;
  }


  public String getActiveChatId() {
    throwIfUninitialised();
    SecretChatEntry chatEntry = activeChat.get();
    return chatEntry != null ? chatEntry.getChat().getId() : null;
  }

  public String getChatOtherUsername(String chatId) {
    throwIfUninitialised();
    if (chatId == null) {
      return null;
    }
    return secretChatEntries.get(chatId).getChat().getOtherUsername();
  }

  public Chat.Configuration getChatConfiguration(String chatId) {
    throwIfUninitialised();
    if (chatId == null) {
      return null;
    }
    return secretChatEntries.get(chatId).getChat().getConfiguration();
  }

  public boolean isChatAboutToDelete(String chatId) {
    throwIfUninitialised();
    SecretChatEntry chatEntry = secretChatEntries.get(chatId);
    return chatEntry != null && (
        chatEntry.getChat().getState().equals(Chat.State.DESERTED) ||
        chatEntry.getChat().getState().equals(Chat.State.DESTROYED));
  }

  public SymmetricCryptoContext getChatCryptoContext(String chatId) {
    throwIfUninitialised();
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
    throwIfUninitialised();
    createOrReconnect(new Chat(chatId, otherUsername, config, Chat.State.PENDING, privateKey),
        false);
  }

  public void createOrReconnectOnAcceptorSide(String chatId, String otherUsername, Chat.Configuration config,
                                              BigInteger privateKey) throws LocalStorageWriteException {
    throwIfUninitialised();
    createOrReconnect(new Chat(chatId, otherUsername, config, Chat.State.AWAITING, privateKey),
        false);
  }

  public void acceptChat(String chatId, String otherUsername,
                         UnaryOperator<BigInteger> sessionKeyGenerator) throws LocalStorageWriteException {
    throwIfUninitialised();
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry, otherUsername);
    BigInteger sessionKey = sessionKeyGenerator.apply(chatEntry.getChat().getKey());
    localStorage.updateChatConfig(chatId, Chat.State.CONNECTED, sessionKey);
    FxUtil.runOnFxThread(() -> {
      updateActiveChatState(chatId, Chat.State.CONNECTED);
      chatEntry.setConnectedState(sessionKey);
      log.info("Established connection to chat: {}", chatEntry.getChat());
    });
  }

  public void disconnectChat(String chatId, String otherUsername) throws LocalStorageWriteException {
    throwIfUninitialised();
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry, otherUsername);
    failRequestingMessages(chatId);
    localStorage.updateChatConfig(chatId, Chat.State.DISCONNECTED, null);
    cryptoContexts.remove(chatId);
    FxUtil.runOnFxThread(() -> {
      updateActiveChatState(chatId, Chat.State.DISCONNECTED);
      chatEntry.setDisconnectedState();
      log.info("Broke connection to chat: {}", chatEntry.getChat());
    });
  }

  public void desertChat(String chatId, String otherUsername) throws LocalStorageWriteException {
    throwIfUninitialised();
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry, otherUsername);
    localStorage.updateChatConfig(chatId, Chat.State.DESERTED, null);
    FxUtil.runOnFxThread(() -> {
      updateActiveChatState(chatId, Chat.State.DESERTED);
      chatEntry.setDesertedState();
      log.info("Chat turned deserted: {}", chatEntry.getChat());
    });
  }

  public void destroyChat(String chatId, String otherUsername)
      throws LocalStorageWriteException, LocalStorageDeletionException {
    throwIfUninitialised();
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry, otherUsername);
    localStorage.updateChatConfig(chatId, Chat.State.DESTROYED, null);
    localStorage.clearChat(chatId);
    FxUtil.runOnFxThread(() -> {
      updateActiveChatState(chatId, Chat.State.DESTROYED);
      messageHolders.get(chatId).getChildren().clear();
      chatEntry.setDestroyedState();
      log.info("Destroyed chat: {}", chatEntry.getChat());
    });
  }

  public void deleteChat(String chatId) throws LocalStorageDeletionException {
    throwIfUninitialised();
    SecretChatEntry chatEntry = secretChatEntries.getOrDefault(chatId, null);
    throwIfIllegalRequest(chatEntry);
    localStorage.deleteChat(chatId);
    cryptoContexts.remove(chatId);
    FxUtil.runOnFxThread(() -> {
      activeChat.set(null);
      activeChatState.set(null);
      chatHolder.getChildren().removeIf(node -> node instanceof SecretChatEntry entry &&
          entry.getChat().getId().equals(chatId));
      updateChatHint();
    });

    secretChatEntries.remove(chatEntry.getChat().getId());
    log.info("Deleted chat: {}", chatEntry.getChat());
  }

  public void insertMessage(String chatId, Message message,
                            boolean isLoadedFromDisk) throws LocalStorageWriteException {
    throwIfUninitialised();
    throwIfIllegalRequest(chatId);
    if (!isLoadedFromDisk) {
      localStorage.saveMessage(chatId, message);
    }

    MessageEntry messageEntry = new MessageEntry(message, hostServices);
    VBox messageHolder = messageHolders.get(chatId);

    messageEntries.put(message.getId(), messageEntry);
    if (!isLoadedFromDisk) {
      processingMessageEntries.put(chatId, messageEntry);
    }

    FxUtil.runOnFxThread(() -> messageHolder.getChildren().add(messageEntry));
    ChainableFuture.runWeaklyAsync(() -> {
      ConcurrentUtil.sleepSafely(200);
      FxUtil.runOnFxThread(() -> {
        if (activeChat.get() != null && activeChat.get().getChat().getId().equals(chatId)) {
          messageHolderWrapper.setVvalue(1);
        }
      });
    });

    FxUtil.runOnFxThread(() -> {
      messageEntry.setOnCancel(() -> {
        if (!message.isBelongedToReceiver() && message.getState().equals(Message.State.DECRYPTING)) {
          try {
            Files.delete(message.getFilePath());
          } catch (IOException ex) {
            log.warn("Failed to delete cancelled file");
          }
        }
        try {
          if (message.isBelongedToReceiver()) {
            localStorage.updateMessageState(chatId, message.getId(), Message.State.CANCELLED);
          } else {
            localStorage.updateMessageState(chatId, message.getId(), Message.State.SENT);
            localStorage.updateMessageFileName(chatId, message.getId(), null);
          }
        } catch (LocalStorageWriteException ex) {
          log.warn("Failed to update message config after user cancellation", ex);
        }
        if (message.isBelongedToReceiver() || message.getText().isEmpty()) {
          messageHolder.getChildren().remove(messageEntry);
        }
      });
      messageEntry.setOnFileRequest(() -> {
        onFileRequest.apply(message).thenWeaklyConsumeAsync(status -> {
          if (status != ClientStatus.OK) {
            log.error("Failed to request file of message '{}' (status {})", message.getId(), status);
          }
        });
      });
    });
  }

  public void insertMessages(String chatId, Collection<Message> messages,
                             boolean isLoadedFromDisk) throws LocalStorageWriteException {
    for (Message msg : messages) {
      insertMessage(chatId, msg, isLoadedFromDisk);
    }
  }

  public void startMessageEncryption(String chatId, String messageId,
                                     CryptoProgress<?> progress, boolean showProgressBar)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    localStorage.updateMessageState(chatId, messageId, Message.State.ENCRYPTING);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startEncryption(progress, showProgressBar));
  }

  public void startMessageUpload(String chatId, String messageId,
                                 Progress<?> progress, boolean showProgressBar)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    localStorage.updateMessageState(chatId, messageId, Message.State.UPLOADING);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startUploading(progress, showProgressBar));
  }

  public void startMessageRequesting(String chatId, String messageId)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    localStorage.updateMessageState(chatId, messageId, Message.State.REQUESTING);
    chatRequestingMessages.get(chatId).add(messageId);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startRequesting());
  }

  public void startMessageDownload(String chatId, String messageId, Path filePath,
                                   Progress<?> progress)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    localStorage.updateMessageFilePath(chatId, messageId, filePath);
    localStorage.updateMessageState(chatId, messageId, Message.State.DOWNLOADING);
    messageEntries.get(messageId).getMessage().setFilePath(filePath);
    chatRequestingMessages.get(chatId).remove(messageId);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startDownloading(progress));
  }

  public void startMessageDecryption(String chatId, String messageId, CryptoProgress<?> progress)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    localStorage.updateMessageState(chatId, messageId, Message.State.DECRYPTING);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).startDecryption(progress));
  }

  public void failMessage(String chatId, String messageId)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    localStorage.updateMessageState(chatId, messageId, Message.State.FAILED);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).fail());
  }

  public void cancelMessage(String chatId, String messageId)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    localStorage.updateMessageState(chatId, messageId, Message.State.CANCELLED);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).cancel());
  }

  public void completeMessage(String chatId, String messageId)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    localStorage.updateMessageState(chatId, messageId, Message.State.SENT);
    processingMessageEntries.remove(messageId);
    FxUtil.runOnFxThread(() -> messageEntries.get(messageId).complete());
  }



  private void createOrReconnect(Chat chat, boolean isLoadedFromDisk) throws LocalStorageWriteException {
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
      updateActiveChatState(chat.getId(), chat.getState());
      return;
    }

    chatEntry = new SecretChatEntry(chat);
    VBox messageHolder = new VBox();

    secretChatEntries.put(chat.getId(), chatEntry);
    messageHolders.put(chat.getId(), messageHolder);
    chatRequestingMessages.put(chat.getId(), Collections.newSetFromMap(new ConcurrentHashMap<>()));

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
      updateChatHint();
      log.debug("Created chat: {}", chat);
    });
  }

  private void failRequestingMessages(String chatId) throws LocalStorageWriteException {
    Set<String> requestingMessages = chatRequestingMessages.get(chatId);
    for (String messageId : new ArrayList<>(requestingMessages)) {
      localStorage.updateMessageState(chatId, messageId, Message.State.FAILED);
      FxUtil.runOnFxThread(() -> messageEntries.get(messageId).fail());
      requestingMessages.remove(messageId);
    }
  }



  private void onSecretChatEntryClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY) &&
        e.getSource() instanceof SecretChatEntry entry) {
      activeChat.set(entry);
    }
  }

  private void updateChatHint() {
    if (chatHolder.getChildren().isEmpty()) {
      chatHint.setText("Нажмите на (+), чтобы создать чат");
    } else {
      chatHint.setText("Выберите чат");
    }
  }

  private void updateActiveChatState(String chatId, Chat.State state) {
    if (activeChat.get() != null && activeChat.get().getChat().getId().equals(chatId)) {
      FxUtil.runOnFxThread(() -> activeChatState.set(state));
    }
  }

  private void throwIfUninitialised() {
    if (chatHolder == null || onFileRequest == null) {
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