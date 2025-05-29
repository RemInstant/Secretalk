package org.reminstant.secretalk.client.application;

import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.rgielen.fxweaver.core.FxWeaver;
import org.reminstant.concurrent.ChainExecutionException;
import org.reminstant.concurrent.Progress;
import org.reminstant.concurrent.functions.ThrowingSupplier;
import org.reminstant.secretalk.client.exception.*;
import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.concurrent.ConcurrentUtil;
import org.reminstant.concurrent.functions.ThrowingFunction;
import org.reminstant.cryptography.CryptoProvider;
import org.reminstant.cryptography.context.CryptoProgress;
import org.reminstant.cryptography.context.SymmetricCryptoContext;
import org.reminstant.secretalk.client.component.DiffieHellmanGenerator;
import org.reminstant.secretalk.client.component.ServerClient;
import org.reminstant.secretalk.client.dto.DHResponse;
import org.reminstant.secretalk.client.dto.JwtResponse;
import org.reminstant.secretalk.client.dto.NoPayloadResponse;
import org.reminstant.secretalk.client.dto.UserEventWrapperResponse;
import org.reminstant.secretalk.client.model.HttpReceivingProgress;
import org.reminstant.secretalk.client.model.HttpSendingProgress;
import org.reminstant.secretalk.client.model.Message;
import org.reminstant.secretalk.client.model.Chat;
import org.reminstant.secretalk.client.model.event.*;
import org.reminstant.secretalk.client.repository.LocalStorage;
import org.reminstant.secretalk.client.util.ClientStatus;
import org.reminstant.secretalk.client.util.FxUtil;
import org.springframework.stereotype.Component;

import java.beans.Visibility;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.nio.file.StandardOpenOption.*;

@Slf4j
@Component
public class ApplicationStateManager {

  private static final long EVENT_CYCLE_TIMEOUT = 30000;
  private static final int DH_PRIVATE_KEY_BIT_LENGTH = 512;
  private static final int FILE_PART_SIZE = 128 * (1 << 10);

  private static final ThrowingFunction<Exception, Integer> defaultHandler;

  private final FxWeaver fxWeaver;
  private final ServerClient serverClient;
  private final ChatManager chatManager;

  private final AtomicBoolean isEventCycleWorking;
  private final Map<String, MessageLoadBundle> currentLoads;
  private final LocalStorage localStorage;

  @Getter
  private Stage stage = null;
  private Scene loginScene = null;
  private Scene mainScene = null;
  private ChainableFuture<?> eventCycleFuture = null;
  private DiffieHellmanGenerator dh = null;

  static {
    defaultHandler = rawEx -> {
      while ((rawEx instanceof ExecutionException || rawEx instanceof ChainExecutionException) &&
          rawEx.getCause() instanceof Exception ex) {
        rawEx = ex;
      }
      return switch (rawEx) {
        // GENERAL
        case ModuleInitialisationException ex -> {
          log.error("Failed to initialise one of app modules", ex);
          yield ClientStatus.MODULE_INITIALISATION_FAILURE;
        }
        case ModuleUninitialisedStateException ex -> {
          log.error("Accessed an uninitialised module", ex);
          yield ClientStatus.MODULE_UNINITIALISED_ACCESS;
        }
        case ServerConnectionException ex -> {
          log.error("Failed to connect to the http server", ex);
          yield ClientStatus.SERVER_CONNECTION_FAILURE;
        }
        // SERVER
        case UnexpectedServerResponseException ex -> {
          log.error("Got unexpected server response", ex);
          yield ClientStatus.SERVER_UNEXPECTED_RESPONSE;
        }
        case UnparsableServerResponseException ex -> {
          log.error("Got unparsable server response", ex);
          yield ClientStatus.SERVER_RESPONSE_PARSE_FAILURE;
        }
        case ServerResponseException ex -> {
          log.error("Server response processing exception", ex);
          yield ClientStatus.SERVER_RESPONSE_ERROR;
        }
        // CHAT MANAGER
        case IllegalChatManagerRequest ex -> {
          log.error("Got illegal chat request (probably foreign)", ex);
          yield ClientStatus.CHAT_ILLEGAL_REQUEST;
        }
        // LOCAL STORAGE
        case LocalStorageReadException ex -> {
          log.error("Failed to read data from the local storage", ex);
          yield ClientStatus.STORAGE_READ_FAILURE;
        }
        case LocalStorageWriteException ex -> {
          log.error("Failed to write data into the local storage", ex);
          yield ClientStatus.STORAGE_WRITE_FAILURE;
        }
        case LocalStorageExistenceException ex -> {
          log.error("Failed to find file in the local storage", ex);
          yield ClientStatus.STORAGE_EXISTENCE_ERROR;
        }
        case LocalStorageCreationException ex -> {
          log.error("Failed to create file in the local storage", ex);
          yield ClientStatus.STORAGE_CREATION_FAILURE;
        }
        case LocalStorageDeletionException ex -> {
          log.error("Failed to delete file in the local storage", ex);
          yield ClientStatus.STORAGE_DELETION_FAILURE;
        }
        // OTHER
        case CancellationException _ -> {
          log.trace("Message transmission was gracefully cancelled");
          yield ClientStatus.OK;
        }
        case ChainTransportIntException ex -> ex.getCargo();
        default -> {
          log.error("Unhandled exception", rawEx);
          throw rawEx;
        }
      };
    };
  }

  public ApplicationStateManager(FxWeaver fxWeaver,
                                 ServerClient serverClient,
                                 ChatManager chatManager, LocalStorage localStorage) {
    this.fxWeaver = fxWeaver;
    this.serverClient = serverClient;
    this.chatManager = chatManager;

    this.isEventCycleWorking = new AtomicBoolean(false);
    this.currentLoads = new HashMap<>();
    this.localStorage = localStorage;
  }

  public void init(Stage stage) {
    this.stage = stage;
    stage.setTitle("Secretalk");
    showLoginScene(); // NOSONAR
//    showMainScene(); // NOSONAR
    try {
//      localStorage.init("anonymous"); // NOSONAR
//      chatManager.loadChats(); // NOSONAR
    } catch (Exception ex) {
      try {
        defaultHandler.apply(ex);
      } catch (Exception ex2) {
        log.error("Got unexpected exception", ex2);
      }
    }
//    serverClient.saveCredentials("anonymous", ""); // NOSONAR
  }

  public void initChatManager(Pane chatHolder, Label chatHint, VBox chatBlock,
                              Runnable onChatOpening, Runnable onChatClosing, Runnable onChatChanging) {
    Function<Message, ChainableFuture<Integer>> onFileRequest = message -> {
      String activeChatId = chatManager.getActiveChatId();
      String otherUsername = chatManager.getChatOtherUsername(activeChatId);

      DirectoryChooser directoryChooser = new DirectoryChooser();
      File file = directoryChooser.showDialog(stage.getScene().getWindow());
      if (file == null) {
        return ChainableFuture.supplyWeaklyAsync(() -> ClientStatus.OK);
      }

      Path path = file.toPath().resolve(message.getFileName());
      return processFileRequest(message.getId(), activeChatId, otherUsername, path);
    };

    try {
      chatManager.initObjects(chatHolder, chatHint, chatBlock);
      chatManager.initBehaviour(onChatOpening, onChatClosing, onChatChanging, onFileRequest);
    } catch (ModuleInitialisationException ex) {
      log.error("Failed to initialise chatManager", ex);
      return;
    }


    log.info("ChatManager INITIALIZED");
  }

  public ChainableFuture<Integer> processLogin(String username, String password) {
    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          JwtResponse jwtResponse = serverClient.processLogin(username, password);
          if (!jwtResponse.isOk()) {
            return jwtResponse.getInternalStatus();
          }

          DHResponse dhResponse = serverClient.getDHParams();
          if (!dhResponse.isOk()) {
            return dhResponse.getInternalStatus();
          }

          dh = new DiffieHellmanGenerator(dhResponse.getPrime(), dhResponse.getGenerator());
          serverClient.saveCredentials(username, jwtResponse.getToken());

          localStorage.init(username);

          log.info("LOGGED AS {}", username);
          showMainScene();
          chatManager.loadChats();
          startEventCycle();

          return ClientStatus.OK;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processRegister(String username, String password) {
    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          NoPayloadResponse response = serverClient.processRegister(username, password);
          return response.getInternalStatus();
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public void processLogout() {
    stopEventCycle();

//    for (MessageLoadBundle bundle : currentLoads.values()) {
//      bundle.httpProgress.cancel(true);
//    }
    currentLoads.clear();

    serverClient.eraseCredentials();
    chatManager.reset();
    localStorage.reset();
    log.info("LOG OUT");
    showLoginScene();
  }

  public ChainableFuture<Integer> processChatCreation(String otherUsername, Chat.Configuration config) {
    String chatId = UUID.randomUUID().toString();
    byte[] initVector = CryptoProvider.generateInitVector(config.cryptoSystemName());
    BigInteger randomDelta = CryptoProvider.generateRandomDelta(config.cryptoSystemName());
    config = new Chat.Configuration(config, initVector, randomDelta.toByteArray());

    return processChatRequest(chatId, otherUsername, config);
  }

  public ChainableFuture<Integer> processActiveChatDesertion() {
    String chatId = chatManager.getActiveChatId();
    boolean isChatAboutToDelete = chatManager.isChatAboutToDelete(chatId);
    String otherUsername = chatManager.getChatOtherUsername(chatId);

    if (chatId == null || otherUsername == null) {
      log.error("Tried to desert chat when there no active chats");
      return ChainableFuture.supplyWeaklyAsync(() -> ClientStatus.NOTHING_TO_PROCESS);
    }

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          if (!isChatAboutToDelete) {
            NoPayloadResponse response = serverClient.desertChat(chatId, otherUsername);
            throwTransportIfStatusNotOk(response.getInternalStatus());
          }
          chatManager.deleteChat(chatId);
          return ClientStatus.OK;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processActiveChatDestruction() {
    String chatId = chatManager.getActiveChatId();
    boolean isChatAboutToDelete = chatManager.isChatAboutToDelete(chatId);
    String otherUsername = chatManager.getChatOtherUsername(chatId);

    if (chatId == null || otherUsername == null) {
      log.error("Tried to destroy chat when there no active chats");
      return ChainableFuture.supplyWeaklyAsync(() -> ClientStatus.NOTHING_TO_PROCESS);
    }

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          if (!isChatAboutToDelete) {
            NoPayloadResponse response = serverClient.destroyChat(chatId, otherUsername);
            throwTransportIfStatusNotOk(response.getInternalStatus());
          }
          chatManager.deleteChat(chatId);
          return ClientStatus.OK;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processActiveChatRequest() {
    String chatId = chatManager.getActiveChatId();
    String otherUsername = chatManager.getChatOtherUsername(chatId);
    Chat.Configuration config = chatManager.getChatConfiguration(chatId);
    return processChatRequest(chatId, otherUsername, config);
  }

  public ChainableFuture<Integer> processChatRequest(String chatId, String otherUsername,
                                                     Chat.Configuration config) {
    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          throwIfDHUninitialised();
          BigInteger privateKey = dh.generatePrivateKey(DH_PRIVATE_KEY_BIT_LENGTH);
          BigInteger publicKey = dh.generatePublicKey(privateKey);

          NoPayloadResponse response = serverClient
              .requestChatConnection(chatId, otherUsername, config, publicKey.toString());
          throwTransportIfStatusNotOk(response.getInternalStatus());

          chatManager.createOrReconnectOnRequesterSide(chatId, otherUsername, config, privateKey);
          return ClientStatus.OK;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processActiveChatAcceptance() {
    String chatId = chatManager.getActiveChatId();
    String otherUsername = chatManager.getChatOtherUsername(chatId);
    return processChatAcceptance(chatId, otherUsername);
  }

  public ChainableFuture<Integer> processChatAcceptance(String chatId, String otherUsername) {
    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          throwIfDHUninitialised();
          BigInteger privateKey = dh.generatePrivateKey(DH_PRIVATE_KEY_BIT_LENGTH);
          BigInteger publicKey = dh.generatePublicKey(privateKey);
          UnaryOperator<BigInteger> generator = otherPublicKey -> dh
              .generateSessionKey(privateKey, otherPublicKey);

          NoPayloadResponse response = serverClient
              .acceptChatConnection(chatId, otherUsername, publicKey.toString());
          throwTransportIfStatusNotOk(response.getInternalStatus());

          chatManager.acceptChat(chatId, otherUsername, generator);
          return ClientStatus.OK;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processActiveChatDisconnection() {
    String chatId = chatManager.getActiveChatId();
    String otherUsername = chatManager.getChatOtherUsername(chatId);
    return processChatDisconnection(chatId, otherUsername);
  }

  public ChainableFuture<Integer> processChatDisconnection(String chatId, String otherUsername) {
    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          NoPayloadResponse response = serverClient.breakChatConnection(chatId, otherUsername);
          throwTransportIfStatusNotOk(response.getInternalStatus());
          chatManager.disconnectChat(chatId, otherUsername);
          return ClientStatus.OK;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processSendingMessage(String messageText) {
    String chatId = chatManager.getActiveChatId();
    String otherUsername = chatManager.getChatOtherUsername(chatId);
    SymmetricCryptoContext cryptoContext = chatManager.getChatCryptoContext(chatId);

    byte[] data = messageText.getBytes();
    String messageId = UUID.randomUUID().toString();
    Message message = new Message(messageId, messageText, serverClient.getUsername(),
        null, false, true);

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          chatManager.insertMessage(chatId, message, false);
          CryptoProgress<byte[]> textProgress = cryptoContext.encryptAsync(data);
          chatManager.startMessageEncryption(chatId, messageId, textProgress, false);
          byte[] encText = textProgress.getResult();

          HttpSendingProgress httpProgress = wrapSendingHttp(() -> serverClient
              .sendChatMessage(messageId, chatId, otherUsername, encText));
          chatManager.startMessageUpload(chatId, messageId, httpProgress, false);

          int status = httpProgress.getResult();
          throwTransportIfStatusNotOk(status);

          chatManager.completeMessage(chatId, messageId);
          return ClientStatus.OK;
        })
        .thenWeaklyHandleAsync(ex -> {
          chatManager.failMessage(chatId, messageId);
          throw ex;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processSendingMessage(String messageText, Path filePath, boolean isImage) {
    String chatId = chatManager.getActiveChatId();
    String otherUsername = chatManager.getChatOtherUsername(chatId);
    SymmetricCryptoContext cryptoContext = chatManager.getChatCryptoContext(chatId);

    byte[] data = messageText.getBytes();
    String messageId = UUID.randomUUID().toString();

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          Path resPath = filePath;
          String fileName = filePath.getFileName().toString();
          if (isImage) {
            fileName = messageId + "." + getFileExtension(fileName);
            resPath = localStorage.createResourceFile(fileName);
            localStorage.copyToFile(filePath, resPath);
          }

          String outFileName = (isImage ? "load" : "") + fileName;

          Message message = new Message(messageId, messageText, serverClient.getUsername(),
              fileName, true, resPath, isImage);

          chatManager.insertMessage(chatId, message, false);
          CryptoProgress<byte[]> textProgress = cryptoContext.encryptAsync(data);
          chatManager.startMessageEncryption(chatId, messageId, textProgress, false);
          byte[] encText = textProgress.getResult();

          Path encTmpFilePath = localStorage.createTmpFile(fileName, "enc");
          String in = resPath.toString();
          String out = encTmpFilePath.toString();
          CryptoProgress<Void> fileProgress = cryptoContext.encryptAsync(in, out);
          fileProgress.getFuture()
              .thenWeaklyHandleAsync(getFileCancellationHandler(encTmpFilePath, "encrypting"));

          chatManager.startMessageEncryption(chatId, messageId, fileProgress, true);
          fileProgress.getResult();

          Thread.sleep(100);

          HttpSendingProgress httpProgress;
          if (isImage) {
            byte[] encData = localStorage.readAsBytes(encTmpFilePath);
            localStorage.deleteTmpFile(encTmpFilePath.getFileName().toString());
            httpProgress = wrapSendingHttp(() ->
                serverClient.sendImage(messageId, chatId, otherUsername, outFileName, encData));
          } else {
            httpProgress = processSendingFilePartly(messageId, chatId, otherUsername, encTmpFilePath);
          }

          chatManager.startMessageUpload(chatId, messageId, httpProgress, true);
          int status = httpProgress.getResult();
          throwTransportIfStatusNotOk(status);
          Thread.sleep(500); // cancel gap

          httpProgress = wrapSendingHttp(() -> serverClient
              .sendChatMessage(messageId, chatId, otherUsername, encText, outFileName, isImage));
          chatManager.startMessageUpload(chatId, messageId, httpProgress, false);

          status = httpProgress.getResult();
          throwTransportIfStatusNotOk(status);

          chatManager.completeMessage(chatId, messageId);
          return ClientStatus.OK;
        })
        .thenWeaklyHandleAsync(ex -> {
          chatManager.failMessage(chatId, messageId);
          throw ex;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processFileRequest(String messageId, String chatId,
                                                     String otherUsername, Path filePath) {
    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          Path loadPath = localStorage.createTmpFile(filePath.getFileName() + "_", "");

          HttpReceivingProgress.Counter counter = new HttpReceivingProgress.Counter();
          HttpReceivingProgress httpProgress = new HttpReceivingProgress(counter);
          chatManager.startMessageDownload(chatId, messageId, filePath, httpProgress);
          currentLoads.put(messageId,
              new MessageLoadBundle(messageId, httpProgress, counter, loadPath, filePath));

          NoPayloadResponse response = serverClient.requestMessageFile(messageId, chatId, otherUsername);
          if (!response.isOk()) {
            currentLoads.remove(messageId);
            return response.getInternalStatus();
          }

          return ClientStatus.OK;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }



  private HttpSendingProgress processSendingFilePartly(String messageId, String chatId,
                                                       String otherUser, Path path) {
    HttpSendingProgress.Counter counter = new HttpSendingProgress.Counter();
    ChainableFuture<Integer> future = ChainableFuture
        .supplyWeaklyAsync(() -> {
          try (FileChannel fileChannel = FileChannel.open(path, READ, DELETE_ON_CLOSE)) {
            int partCnt = (int) Math.ceil(1. * fileChannel.size() / FILE_PART_SIZE);
            counter.setSubTaskCount(partCnt);

            for (int i = 0; i < partCnt; ++i) {
              byte[] part = new byte[FILE_PART_SIZE];
              int read = fileChannel.read(ByteBuffer.wrap(part));
              if (read < FILE_PART_SIZE) {
                part = Arrays.copyOf(part, read);
              }
              NoPayloadResponse response = serverClient
                  .sendFilePart(messageId, chatId, otherUser, i, partCnt, part);
              throwTransportIfStatusNotOk(response.getInternalStatus());
              counter.incrementProgress();
            }
            return ClientStatus.OK;
          }
        })
        .thenWeaklyHandleAsync(defaultHandler);
    counter.setFuture(future);
    return new HttpSendingProgress(counter);
  }


  private void startEventCycle() {
    isEventCycleWorking.set(true);
    eventCycleFuture = ChainableFuture.runWeaklyAsync(() -> {
      log.debug("Event cycle is started");
      while (isEventCycleWorking.get()) {
        boolean isOk;
        try {
          isOk = doEventCycle();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          break;
        } catch (Exception ex) {
          log.error("Unexpected unchecked exception is threw in event cycle", ex);
          isOk = false;
        }
        if (!isOk) {
          ConcurrentUtil.sleepSafely(5000);
        }
      }
      log.debug("Event cycle is stopped");
    });
  }

  private void stopEventCycle() {
    isEventCycleWorking.set(false);
    if (eventCycleFuture != null) {
      eventCycleFuture.cancel(true);
    }
  }

  private boolean doEventCycle() throws Exception {
    UserEvent rawEvent;
    try {
      UserEventWrapperResponse wrapper = serverClient.getEvent(EVENT_CYCLE_TIMEOUT);
      rawEvent = UserEvent.getEvent(wrapper.getEventType(), wrapper.getEventJson().getBytes());
    } catch (ServerResponseException ex) {
      log.error("Server response error", ex);
      return false;
    } catch (IOException ex) {
      log.error("Event cycle failed to get event", ex);
      return false;
    }

    if (!isEventCycleWorking.get()) {
      log.debug("ignored event: {}", rawEvent);
      return true;
    }

    if (rawEvent instanceof VoidEvent) {
      return true;
    }

    log.debug("got event: {} ", rawEvent);
    try {
      switch (rawEvent) {
        case ChatDesertEvent e -> handleChatDesertEvent(e);
        case ChatDestroyEvent e -> handleChatDestroyEvent(e);
        case ChatConnectionRequestEvent e -> handleChatConnectionRequestEvent(e);
        case ChatConnectionAcceptEvent e -> handleChatConnectionAcceptEvent(e);
        case ChatConnectionBreakEvent e -> handleChatConnectionBreakEvent(e);
        case ChatMessageEvent e -> handleChatMessageEvent(e);
        case ChatImageEvent e -> handleChatImageEvent(e);
        case ChatFileEvent e -> handleChatFileEvent(e);
        default -> { } // NOSONAR
      }
    } catch (Exception ex) {
      defaultHandler.apply(ex);
    }

    try {
      NoPayloadResponse response = serverClient.acknowledgeEvent(rawEvent.getId());
      if (!response.isOk()) {
        log.error("Failed to acknowledge event #{} (code {})",
            rawEvent.getId(), response.getInternalStatus());
        return false;
      }
    } catch (ServerResponseException ex) {
      log.error("Failed to acknowledge event #{}", rawEvent.getId(), ex);
      return false;
    }

    return true;
  }

  private void handleChatDesertEvent(ChatDesertEvent event) throws LocalStorageWriteException {
    chatManager.desertChat(event.getChatId(), event.getSenderUsername());
  }

  private void handleChatDestroyEvent(ChatDestroyEvent event)
      throws LocalStorageWriteException, LocalStorageDeletionException {
    chatManager.destroyChat(event.getChatId(), event.getSenderUsername());
  }

  private void handleChatConnectionRequestEvent(ChatConnectionRequestEvent event)
      throws LocalStorageWriteException {
    String chatId = event.getChatId();
    String otherUsername = event.getRequesterUsername();
    Chat.Configuration config = event.getChatConfiguration();
    BigInteger publicKey = new BigInteger(event.getPublicKey());

    chatManager.createOrReconnectOnAcceptorSide(chatId, otherUsername, config, publicKey);
  }

  private void handleChatConnectionAcceptEvent(ChatConnectionAcceptEvent event)
      throws LocalStorageWriteException {
    String chatId = event.getChatId();
    String otherUsername = event.getAcceptorUsername();
    BigInteger otherPublicKey = new BigInteger(event.getPublicKey());
    UnaryOperator<BigInteger> generator = secretKey -> dh.generateSessionKey(secretKey, otherPublicKey);

    chatManager.acceptChat(chatId, otherUsername, generator);
  }

  private void handleChatConnectionBreakEvent(ChatConnectionBreakEvent event)
      throws LocalStorageWriteException {
    chatManager.disconnectChat(event.getChatId(), event.getSenderUsername());
  }

  private void handleChatMessageEvent(ChatMessageEvent event)
      throws LocalStorageWriteException, LocalStorageExistenceException {
    String chatId = event.getChatId();
    String messageId = event.getMessageId();
    Path filePath = null;

    if (event.getAttachedFileName() != null && event.isImage()) {
      filePath = localStorage.getResourceFile(event.getAttachedFileName());
    }

    SymmetricCryptoContext cryptoContext = chatManager.getChatCryptoContext(event.getChatId());
    String text = new String(cryptoContext.decrypt(event.getMessageData()));

    Message message = new Message(messageId, text, event.getSenderUsername(),
        event.getAttachedFileName(), false, filePath, event.isImage());

    chatManager.insertMessage(event.getChatId(), message, false);
    if (event.getAttachedFileName() == null || event.isImage()) {
      chatManager.completeMessage(chatId, message.getId());
    } else {
      chatManager.startMessageRequesting(chatId, message.getId());
    }
  }

  private void handleChatImageEvent(ChatImageEvent event)
      throws LocalStorageWriteException, LocalStorageCreationException {
    SymmetricCryptoContext cryptoContext = chatManager.getChatCryptoContext(event.getChatId());
    byte[] decryptedData = cryptoContext.decrypt(event.getImageData());

    Path resPath = localStorage.createResourceFile(event.getFileName());
    localStorage.writeToFile(resPath, decryptedData);
  }

  private void handleChatFileEvent(ChatFileEvent event) throws LocalStorageWriteException {
    MessageLoadBundle bundle = currentLoads.getOrDefault(event.getMessageId(), null);
    String chatId = event.getChatId();
    String messageId = event.getMessageId();

    if (bundle == null) {
      log.error("Got unexpected file part");
      return;
    }
    if (bundle.progress().isCancelled()) {
      if (bundle.loadPath != null) {
        try {
          Files.deleteIfExists(bundle.loadPath);
        } catch (IOException ex) {
          log.warn("Failed to delete loading encrypted file after cancellation", ex);
        }
        MessageLoadBundle cancelledBundle = new MessageLoadBundle(
            messageId, bundle.progress, bundle.progressCounter, null, null);
        currentLoads.put(messageId, cancelledBundle);
      }
      if (event.getPartNumber() + 1 == event.getPartCount()) {
        currentLoads.remove(messageId);
      }
      return;
    }

    if (event.getPartNumber() == 0) {
      bundle.progressCounter.setSubTaskCount(event.getPartCount());
    }

    try (FileChannel fileChannel = FileChannel.open(bundle.loadPath, CREATE, WRITE)) {
      long pos = FILE_PART_SIZE * event.getPartNumber();
      int write = fileChannel.write(ByteBuffer.wrap(event.getFileData()), pos);
      if (write != event.getFileData().length) {
        log.error("Wrong written byte count");
      }
    } catch (IOException ex) {
      log.error("File writing error", ex);
      currentLoads.remove(event.getMessageId());
      chatManager.failMessage(chatId, messageId);
    }

    bundle.progressCounter().incrementProgress();
//    bundle.httpProgress().setProcessedBlocksCount(event.getPartNumber() + 1);

    if (bundle.progress().isDone()) {
      SymmetricCryptoContext cryptoContext = chatManager.getChatCryptoContext(event.getChatId());
      String in = bundle.loadPath.toString();
      String out = bundle.resultPath.toString();
      CryptoProgress<Void> progress = cryptoContext.decryptAsync(in, out);
      chatManager.startMessageDecryption(chatId, messageId, progress);
      progress.getFuture()
          .thenWeaklySupplyAsync(() -> {
            try {
              Files.deleteIfExists(bundle.loadPath);
            } catch (IOException ex) {
              log.warn("Failed to delete loaded encrypted file after decryption", ex);
            }
            chatManager.completeMessage(chatId, messageId);
            return ClientStatus.OK;
          })
          .thenWeaklyHandleAsync(getFileCancellationHandler(bundle.resultPath, "decrypting"))
          .thenWeaklyHandleAsync(getFileCancellationHandler(bundle.loadPath, "loaded encrypted"))
          .thenWeaklyHandleAsync(defaultHandler);
    }
  }


  
  private void showLoginScene() {
    Objects.requireNonNull(stage, "Cannot show login scene because stage is uninitialised");

    if (loginScene == null) {
      Parent root = fxWeaver.loadView(LoginSceneController.class);
      loginScene = new Scene(root);
    }

    FxUtil.runOnFxThread(() -> {
      stage.setScene(loginScene);
      stage.setResizable(false);
      stage.show();
      centerStage();
    });
  }

  private void showMainScene() {
    Objects.requireNonNull(stage, "Cannot show main scene because stage is uninitialised");

    if (mainScene == null) {
      Parent root = fxWeaver.loadView(MainSceneController.class);
      mainScene = new Scene(root);
    }

    FxUtil.runOnFxThread(() -> {
      stage.setScene(mainScene);
      stage.setMinWidth(500.0);
      stage.setMinHeight(300.0);
      stage.setResizable(true);
      stage.show();
      centerStage();
    });
  }

  private void centerStage() {
    Rectangle2D primScreenBounds = Screen.getPrimary().getVisualBounds();
    stage.setX((primScreenBounds.getWidth() - stage.getWidth()) / 2);
    stage.setY((primScreenBounds.getHeight() - stage.getHeight()) / 2);
  }

  private <T> ThrowingFunction<Exception, T> getFileCancellationHandler(Path path, String filePrefix) {
    return originEx -> {
      Exception ex = originEx;
      while (ex instanceof ChainExecutionException && ex.getCause() instanceof Exception cause) {
        ex = cause;
      }
      if (ex instanceof CancellationException) {
        try {
          Files.deleteIfExists(path);
        } catch (IOException ioEx) {
          log.warn("Failed to delete {} file after cancellation", filePrefix, ioEx);
        }
      }
      throw originEx;
    };
  }


  private HttpSendingProgress wrapSendingHttp(ThrowingSupplier<NoPayloadResponse> httpAction) {
    HttpSendingProgress.Counter counter = new HttpSendingProgress.Counter();
    counter.setFuture(ChainableFuture.supplyWeaklyAsync(() -> {
      counter.setSubTaskCount(1);
      NoPayloadResponse response = httpAction.get();
      counter.incrementProgress();
      return response.getInternalStatus();
    }));

    return new HttpSendingProgress(counter);
  }

  private String getFileExtension(String fileName) {
    int dotIndex = fileName.lastIndexOf('.');
    return fileName.substring(dotIndex + 1);
  }

  private void throwTransportIfStatusNotOk(int status) {
    if (status != ClientStatus.OK) {
      throw new ChainTransportIntException(status);
    }
  }
  
  private void throwIfDHUninitialised() {
    if (dh == null) {
      throw new ModuleUninitialisedStateException("DH is uninitialised");
    }
  }

  private record MessageLoadBundle(
      String messageId,
      Progress<?> progress,
      Progress.Counter progressCounter,
      Path loadPath,
      Path resultPath) {
  }
}
