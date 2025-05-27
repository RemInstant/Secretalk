package org.reminstant.secretalk.client.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.rgielen.fxweaver.core.FxWeaver;
import org.reminstant.concurrent.ChainExecutionException;
import org.reminstant.concurrent.Progress;
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
import org.reminstant.secretalk.client.util.FxUtil;
import org.springframework.stereotype.Component;

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

  private static final String DIFFIE_HELLMAN_NOT_INIT_LOG = "DiffieHellmanGenerator is uninitialised";

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
        case InvalidServerAnswer ex -> {
          log.error("Got invalid server answer", ex);
          yield 602;
        }
        case LocalStorageReadException ex -> {
          log.error("Failed to read data from the local storage", ex);
          yield 603;
        }
        case LocalStorageWriteException ex -> {
          log.error("Failed to write data into the local storage", ex);
          yield 604;
        }
        case LocalStorageTmpFileException ex -> {
          log.error("Failed to create tmp file in the local storage", ex);
          yield 605;
        }
        case ModuleInitialisationException ex -> {
          log.error("Failed to initialise one of app modules", ex);
          yield 606;
        }
        case IllegalChatManagerRequest ex -> {
          log.error("Got illegal chat request (probably foreign)", ex);
          yield 607;
        }
        case LocalStorageDeletionException ex -> {
          log.error("Failed to delete file in the local storage", ex);
          yield 608;
        }
        case ModuleUninitialisedStateException ex -> {
          log.error("Accessed an uninitialised module", ex);
          yield 609;
        }
        case CancellationException _ -> {
          log.trace("Message transmission was gracefully cancelled");
          yield 610;
        }
        case IOException ex -> {
          log.error("Failed to connect to the http server", ex);
          yield 0;
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

  public void initChatManager(Pane chatHolder, Label chatTitle,
                              StackPane chatStateBlockHolder, ScrollPane messageHolderWrapper,
                              Runnable onChatOpening, Runnable onChatClosing, Runnable onChatChanging) {
    Function<Message, ChainableFuture<Integer>> onFileRequest = message -> {
      String activeChatId = chatManager.getActiveChatId();
      String otherUsername = chatManager.getChatOtherUsername(activeChatId);

      DirectoryChooser directoryChooser = new DirectoryChooser();
      File file = directoryChooser.showDialog(stage.getScene().getWindow());
      if (file == null) {
        return ChainableFuture.supplyWeaklyAsync(() -> 200);
      }

      Path path = file.toPath().resolve(message.getFileName());
      return processFileRequest(message.getId(), activeChatId, otherUsername, path);
    };

    chatManager.initObjects(chatHolder, chatTitle, chatStateBlockHolder, messageHolderWrapper);
    chatManager.initBehaviour(onChatOpening, onChatClosing, onChatChanging, onFileRequest);
    log.info("ChatManager INITIALIZED");
  }

  public ChainableFuture<Integer> processLogin(String username, String password) {
    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          JwtResponse jwtResponse = serverClient.processLogin(username, password);
          if (jwtResponse.getStatus() != 200) {
            return jwtResponse.getStatus();
          }

          DHResponse dhResponse = serverClient.getDHParams();
          if (dhResponse.getStatus() != 200) {
            return dhResponse.getStatus();
          }

          dh = new DiffieHellmanGenerator(dhResponse.getPrime(), dhResponse.getGenerator());
          serverClient.saveCredentials(username, jwtResponse.getToken());

          localStorage.init(username);

          log.info("LOGGED AS {}", username);
          showMainScene();
          chatManager.loadChats();
          startEventCycle();

          return 200;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processRegister(String username, String password) {
    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          NoPayloadResponse response = serverClient.processRegister(username, password);
          return response.getStatus();
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
      return ChainableFuture.supplyWeaklyAsync(() -> 601);
    }

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          if (!isChatAboutToDelete) {
            NoPayloadResponse response = serverClient.desertChat(chatId, otherUsername);
            throwTransportIfStatusNotOk(response.getStatus());
          }
          chatManager.deleteChat(chatId);
          return 200;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processActiveChatDestruction() {
    String chatId = chatManager.getActiveChatId();
    boolean isChatAboutToDelete = chatManager.isChatAboutToDelete(chatId);
    String otherUsername = chatManager.getChatOtherUsername(chatId);

    if (chatId == null || otherUsername == null) {
      log.error("Tried to destroy chat when there no active chats");
      return ChainableFuture.supplyWeaklyAsync(() -> 601);
    }

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          if (!isChatAboutToDelete) {
            NoPayloadResponse response = serverClient.destroyChat(chatId, otherUsername);
            throwTransportIfStatusNotOk(response.getStatus());
          }
          chatManager.deleteChat(chatId);
          return 200;
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
    if (dh == null) {
      log.error(DIFFIE_HELLMAN_NOT_INIT_LOG);
      return ChainableFuture.supplyWeaklyAsync(() -> 601);
    }

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          BigInteger privateKey = dh.generatePrivateKey(DH_PRIVATE_KEY_BIT_LENGTH);
          BigInteger publicKey = dh.generatePublicKey(privateKey);

          NoPayloadResponse response = serverClient
              .requestChatConnection(chatId, otherUsername, config, publicKey.toString());
          throwTransportIfStatusNotOk(response.getStatus());

          chatManager.createOrReconnectOnRequesterSide(chatId, otherUsername, config, privateKey);
          return 200;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processActiveChatAcceptance() {
    String chatId = chatManager.getActiveChatId();
    String otherUsername = chatManager.getChatOtherUsername(chatId);
    return processChatAcceptance(chatId, otherUsername);
  }

  public ChainableFuture<Integer> processChatAcceptance(String chatId, String otherUsername) {
    if (dh == null) {
      log.error(DIFFIE_HELLMAN_NOT_INIT_LOG);
      return ChainableFuture.supplyWeaklyAsync(() -> 601);
    }

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          BigInteger privateKey = dh.generatePrivateKey(DH_PRIVATE_KEY_BIT_LENGTH);
          BigInteger publicKey = dh.generatePublicKey(privateKey);
          UnaryOperator<BigInteger> generator = otherPublicKey -> dh
              .generateSessionKey(privateKey, otherPublicKey);

          NoPayloadResponse response = serverClient
              .acceptChatConnection(chatId, otherUsername, publicKey.toString());
          throwTransportIfStatusNotOk(response.getStatus());

          chatManager.acceptChat(chatId, otherUsername, generator);
          return 200;
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
          throwTransportIfStatusNotOk(response.getStatus());
          chatManager.disconnectChat(chatId, otherUsername);
          return 200;
        })
        .thenWeaklyHandleAsync(defaultHandler);
  }

  public ChainableFuture<Integer> processSendingMessage(String messageText, File file) {
    String chatId = chatManager.getActiveChatId();
    String otherUsername = chatManager.getChatOtherUsername(chatId);
    SymmetricCryptoContext cryptoContext = chatManager.getChatCryptoContext(chatId);

    byte[] data = messageText.getBytes();
    String messageId = UUID.randomUUID().toString();
    Path filePath = file != null ? file.toPath() : null;
    String fileName = file != null ? file.getName() : null;
    Message message = new Message(
        messageId, messageText, serverClient.getUsername(), fileName, true, filePath);

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          chatManager.insertMessage(chatId, message, false);
          CryptoProgress<byte[]> textProgress = cryptoContext.encryptAsync(data);
          chatManager.startMessageEncryption(chatId, messageId, textProgress, false);
          byte[] encText = textProgress.getResult();

          Path encTmpFilePath;
          if (file != null) {
            encTmpFilePath = localStorage.createTmpFile(fileName, "enc");
            String in = file.getPath();
            String out = encTmpFilePath.toString();
            CryptoProgress<Void> fileProgress = cryptoContext.encryptAsync(in, out);
            fileProgress.getFuture()
                .thenWeaklyHandleAsync(getFileCancellationHandler(encTmpFilePath, "encrypting"));

            chatManager.startMessageEncryption(chatId, messageId, fileProgress, true);
            fileProgress.getResult();
          } else {
            encTmpFilePath = null;
          }

          Thread.sleep(100);

          if (file != null) {
            HttpSendingProgress httpProgress = processSendingFilePartly(
                messageId, chatId, otherUsername, encTmpFilePath);
            chatManager.startMessageUpload(chatId, messageId, httpProgress, true);
            int status = httpProgress.getResult();
            throwTransportIfStatusNotOk(status);
            Thread.sleep(500); // cancel gap
          }

          HttpSendingProgress.Counter counter = new HttpSendingProgress.Counter();
          counter.setFuture(ChainableFuture.supplyWeaklyAsync(() -> {
            counter.setSubTaskCount(1);
            NoPayloadResponse response = serverClient
                .sendChatMessage(messageId, chatId, otherUsername, encText, fileName);
            counter.incrementProgress();
            return response.getStatus();
          }));

          HttpSendingProgress httpProgress = new HttpSendingProgress(counter);
          chatManager.startMessageUpload(chatId, messageId, httpProgress, false);

          int status = httpProgress.getResult();
          throwTransportIfStatusNotOk(status);

          chatManager.completeMessage(chatId, messageId);
          return 200;
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
          if (response.getStatus() != 200) {
            currentLoads.remove(messageId);
            return response.getStatus();
          }

          return 200;
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
              throwTransportIfStatusNotOk(response.getStatus());
              counter.incrementProgress();
            }
            return 200;
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
    UserEvent rawEvent = null;
    try {
      UserEventWrapperResponse wrapper = serverClient.getEvent(EVENT_CYCLE_TIMEOUT);
      if (!Thread.interrupted()) {
        rawEvent = UserEvent.getEvent(wrapper.getEventType(), wrapper.getEventJson().getBytes());
      }
    } catch (JsonProcessingException ex) {
      log.error("Event cycle failed to parse event", ex);
      return false;
    } catch (IOException ex) {
      log.error("Event cycle failed to get event", ex);
      return false;
    } catch (InvalidServerAnswer ex) {
      log.error("Event cycle failed to parse server answer", ex);
      return false;
    }

    if (!isEventCycleWorking.get()) {
      if (rawEvent != null) {
        log.debug("ignored event: {}", rawEvent);
      }
      return true;
    }

    if (rawEvent == null) {
      log.warn("got null event");
      return false;
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
        case ChatFileEvent e -> handleChatFileEvent(e);
        default -> { } // NOSONAR
      }
    } catch (Exception ex) {
      defaultHandler.apply(ex);
    }

    try {
      NoPayloadResponse response = serverClient.acknowledgeEvent(rawEvent.getId());
      if (response.getStatus() != 200) {
        log.error("Failed to acknowledge event #{} (code {})", rawEvent.getId(), response.getStatus());
        return false;
      }
    } catch (IOException ex) {
      log.error("Failed to acknowledge event #{} (failed to connect)", rawEvent.getId(), ex);
      return false;
    } catch (InvalidServerAnswer ex) {
      log.error("Failed to acknowledge event #{} (failed to parse answer)", rawEvent.getId(), ex);
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

  private void handleChatMessageEvent(ChatMessageEvent event) throws LocalStorageWriteException {
    String chatId = event.getChatId();
    SymmetricCryptoContext cryptoContext = chatManager.getChatCryptoContext(event.getChatId());
    String text = new String(cryptoContext.decrypt(event.getMessageData()));
    Message message = new Message(event.getMessageId(), text,
        event.getSenderUsername(), event.getAttachedFileName(), false);

    chatManager.insertMessage(event.getChatId(), message, false);
    if (event.getAttachedFileName() == null) {
      chatManager.completeMessage(chatId, message.getId());
    } else {
      chatManager.startMessageRequesting(chatId, message.getId());
    }
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
            return 200;
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
      stage.setMinWidth(460.0);
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
  
  private void throwTransportIfStatusNotOk(int status) {
    if (status != 200) {
      throw new ChainTransportIntException(status);
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
