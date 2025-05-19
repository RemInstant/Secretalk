package org.reminstant.cryptomessengerclient.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.rgielen.fxweaver.core.FxWeaver;
import org.reminstant.concurrent.ChainExecutionException;
import org.reminstant.concurrent.ChainTransportIntException;
import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.concurrent.ConcurrentUtil;
import org.reminstant.concurrent.functions.ThrowingFunction;
import org.reminstant.cryptography.CryptoProvider;
import org.reminstant.cryptography.context.CryptoProgress;
import org.reminstant.cryptography.context.SymmetricCryptoContext;
import org.reminstant.cryptomessengerclient.application.control.MessageEntry;
import org.reminstant.cryptomessengerclient.component.DiffieHellmanGenerator;
import org.reminstant.cryptomessengerclient.component.ServerClient;
import org.reminstant.cryptomessengerclient.dto.DHResponse;
import org.reminstant.cryptomessengerclient.dto.JwtResponse;
import org.reminstant.cryptomessengerclient.dto.NoPayloadResponse;
import org.reminstant.cryptomessengerclient.dto.UserEventWrapperResponse;
import org.reminstant.cryptomessengerclient.exception.InvalidServerAnswer;
import org.reminstant.cryptomessengerclient.model.Message;
import org.reminstant.cryptomessengerclient.model.Chat;
import org.reminstant.cryptomessengerclient.model.event.*;
import org.reminstant.cryptomessengerclient.util.FxUtil;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.nio.file.StandardOpenOption.*;

@Slf4j
@Component
public class ApplicationStateManager {

  private static final long EVENT_CYCLE_TIMEOUT = 30000;
  private static final int DH_PRIVATE_KEY_BIT_LENGTH = 512;
  private static final int FILE_PART_SIZE = 512;

  private static final String CONNECTION_FAILURE_LOG = "Failed to connect to the http server";
  private static final String INVALID_SERVER_ANSWER_LOG = "Got invalid server answer";
  private static final String DIFFIE_HELLMAN_NOT_INIT_LOG = "DiffieHellmanGenerator is uninitialised";

  private static final ThrowingFunction<Exception, Integer> defaultHandler;

  private final FxWeaver fxWeaver;
  private final ServerClient serverClient;
  private final ChatManager chatManager;

  private final AtomicBoolean isEventCycleWorking;
  private final Map<String, MessageLoadBundle> currentLoads;

  private Stage stage = null;
  private Scene loginScene = null;
  private Scene mainScene = null;
  private DiffieHellmanGenerator dh = null;

  static {
    defaultHandler = rawEx -> {
      while (rawEx instanceof ChainExecutionException &&
          rawEx.getCause() instanceof Exception ex) {
        rawEx = ex;
      }
      return switch (rawEx) {
        case IOException ex -> {
          log.error(CONNECTION_FAILURE_LOG, ex);
          yield 0;
        }
        case InvalidServerAnswer ex -> {
          log.error(INVALID_SERVER_ANSWER_LOG, ex);
          yield 602;
        }
        case ChainTransportIntException ex -> ex.getCargo();
        default -> throw rawEx;
      };
    };
  }

  public ApplicationStateManager(FxWeaver fxWeaver,
                                 ServerClient serverClient,
                                 ChatManager chatManager) {
    this.fxWeaver = fxWeaver;
    this.serverClient = serverClient;
    this.chatManager = chatManager;

    this.isEventCycleWorking = new AtomicBoolean(false);
    this.currentLoads = new HashMap<>();
  }

  public void init(Stage stage) {
    this.stage = stage;
    stage.setTitle("123");
//    showLoginScene(); // NOSONAR
    showMainScene(); // NOSONAR
    chatManager.loadChats(); // NOSONAR
    serverClient.saveCredentials("anonymous", ""); // NOSONAR
  }

  public void initChatManager(Pane chatHolder, Label chatTitle,
                              StackPane chatStateBlockHolder, ScrollPane messageHolderWrapper,
                              Runnable onChatOpening, Runnable onChatClosing, Runnable onChatChanging) {
    Function<Chat, ChainableFuture<Integer>> onChatRequest =
        chat -> processChatRequest(chat.getId(), chat.getOtherUsername(), chat.getConfiguration());

    Function<Chat, ChainableFuture<Integer>> onChatAccept =
        chat -> processChatAcceptance(chat.getId(), chat.getOtherUsername());

    Function<Chat, ChainableFuture<Integer>> onChatDisconnect =
        chat -> processChatDisconnect(chat.getId(), chat.getOtherUsername());

    chatManager.initObjects(chatHolder, chatTitle, chatStateBlockHolder, messageHolderWrapper);
    chatManager.initBehaviour(
        onChatOpening, onChatClosing, onChatChanging,
        onChatRequest, onChatAccept, onChatDisconnect);
    log.info("ChatManager INITIALIZED");
  }

  public ChainableFuture<Integer> processLogin(String username, String password) {
    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          JwtResponse jwtResponse = serverClient.processLogin(username, password);
          throwTransportIfStatusNotOk(jwtResponse.getStatus());

          DHResponse dhResponse = serverClient.getDHParams();
          throwTransportIfStatusNotOk(dhResponse.getStatus());

          dh = new DiffieHellmanGenerator(dhResponse.getPrime(), dhResponse.getGenerator());
          serverClient.saveCredentials(username, jwtResponse.getToken());

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
    serverClient.eraseCredentials();
    chatManager.reset();
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

  public ChainableFuture<Integer> processChatDeserting() {
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

  public ChainableFuture<Integer> processChatDestroying() {
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

  public ChainableFuture<Integer> processChatDisconnect(String chatId, String otherUsername) {
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

    Message message = new Message(
        messageId, messageText, serverClient.getUsername(), filePath, true);
    MessageEntry messageEntry = chatManager.insertMessage(chatId, message);

    return ChainableFuture
        .supplyWeaklyAsync(() -> {
          Path encTmpFilePath = null;
          String fileName = file != null ? file.getName() : null;

          CryptoProgress<byte[]> textProgress = cryptoContext.encryptAsync(data);
          FxUtil.runOnFxThread(messageEntry::startEncrypting);
          byte[] encText = textProgress.getResult();

          if (file != null) {
            encTmpFilePath = Files.createTempFile(fileName, "enc");
            String in = file.getPath();
            String out = encTmpFilePath.toString();
            CryptoProgress<Void> fileProgress = cryptoContext.encryptAsync(in, out);
            FxUtil.runOnFxThread(() -> messageEntry.startEncrypting(fileProgress));
            fileProgress.getResult();
          }

          Thread.sleep(200);
          FxUtil.runOnFxThread(messageEntry::startTransmission);
          NoPayloadResponse response = serverClient
              .sendChatMessage(messageId, chatId, otherUsername, encText, fileName);
          throwTransportIfStatusNotOk(response.getStatus());

          if (file != null) {
            HttpMultipartProgress httpProgress = processSendingFilePartly(
                messageId, chatId, otherUsername, encTmpFilePath);
            FxUtil.runOnFxThread(() -> messageEntry.startTransmission(httpProgress));
            int status = httpProgress.getResult();
            throwTransportIfStatusNotOk(status);
          }

          FxUtil.runOnFxThread(messageEntry::complete);
          return 200;
        })
        .thenWeaklyHandleAsync(ex -> {
          FxUtil.runOnFxThread(messageEntry::fail);
          log.error("Some ex", ex);
          return 600;
        });
  }



  private HttpMultipartProgress processSendingFilePartly(String messageId, String chatId,
                                                         String otherUser, Path path) {
    HttpMultipartProgress progress = new HttpMultipartProgress();
    ChainableFuture<Integer> future = ChainableFuture.supplyWeaklyAsync(() -> {
      try (FileChannel fileChannel = FileChannel.open(path, READ, DELETE_ON_CLOSE)) {
        int partCnt = (int) Math.ceil(1. * fileChannel.size() / FILE_PART_SIZE);
        progress.setRequestsCount(partCnt);

        for (int i = 0; i < partCnt; ++i) {
          byte[] part = new byte[FILE_PART_SIZE];
          int read = fileChannel.read(ByteBuffer.wrap(part));
          if (read < FILE_PART_SIZE) {
            part = Arrays.copyOf(part, read);
          }
          try {
            NoPayloadResponse response = serverClient
                .sendFilePart(messageId, chatId, otherUser, i, partCnt, part);
            if (response.getStatus() != 200) {
              return response.getStatus();
            }
          } catch (IOException ex) {
            log.error(CONNECTION_FAILURE_LOG, ex);
            return 0;
          } catch (InvalidServerAnswer ex) {
            log.error(INVALID_SERVER_ANSWER_LOG, ex);
            return 602;
          }
          progress.incrementProcessedBlocksCount();
        }
        return 200;
      }
    });
    progress.setFuture(future);
    return progress;
  }


  private void startEventCycle() {
    isEventCycleWorking.set(true);
    CompletableFuture.runAsync(() -> {
      while (isEventCycleWorking.get()) {
        boolean isOk = true;
        try {
          isOk = doEventCycle();
        } catch (Exception ex) {
          log.error("Unexpected unchecked exception is threw in event cycle", ex);
          isOk = false;
        }
        if (!isOk) {
          ConcurrentUtil.sleepSafely(1000);
        }
      }
    });
  }

  private void stopEventCycle() {
    isEventCycleWorking.set(false);
  }

  private boolean doEventCycle() {
    UserEvent rawEvent;
    try {
      UserEventWrapperResponse wrapper = serverClient.getEvent(EVENT_CYCLE_TIMEOUT);
      rawEvent = UserEvent.getEvent(wrapper.getEventType(), wrapper.getEventJson().getBytes());
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
      log.debug("ignored event: {}", rawEvent);
      return true;
    }

    log.debug("got event: {}", rawEvent);
    switch (rawEvent) {
      case VoidEvent _ -> { return true; }
      case ChatDesertEvent e -> handleChatDesertEvent(e);
      case ChatDestroyEvent e -> handleChatDestroyEvent(e);
      case ChatConnectionRequestEvent e -> handleChatConnectionRequestEvent(e);
      case ChatConnectionAcceptEvent e -> handleChatConnectionAcceptEvent(e);
      case ChatConnectionBreakEvent e -> handleChatConnectionBreakEvent(e);
      case ChatMessageEvent e -> handleChatMessageEvent(e);
      case ChatFileEvent e -> handleChatFileEvent(e);
      default -> {} // NOSONAR
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

  private void handleChatDesertEvent(ChatDesertEvent event) {
    chatManager.desertChat(event.getChatId(), event.getSenderUsername());
  }

  private void handleChatDestroyEvent(ChatDestroyEvent event) {
    chatManager.destroyChat(event.getChatId(), event.getSenderUsername());
  }

  private void handleChatConnectionRequestEvent(ChatConnectionRequestEvent event) {
    String chatId = event.getChatId();
    String otherUsername = event.getRequesterUsername();
    Chat.Configuration config = event.getChatConfiguration();
    BigInteger publicKey = new BigInteger(event.getPublicKey());

    chatManager.createOrReconnectOnAcceptorSide(chatId, otherUsername, config, publicKey);
  }

  private void handleChatConnectionAcceptEvent(ChatConnectionAcceptEvent event) {
    String chatId = event.getChatId();
    String otherUsername = event.getAcceptorUsername();
    BigInteger otherPublicKey = new BigInteger(event.getPublicKey());
    UnaryOperator<BigInteger> generator = secretKey -> dh.generateSessionKey(secretKey, otherPublicKey);

    chatManager.acceptChat(chatId, otherUsername, generator);
  }

  private void handleChatConnectionBreakEvent(ChatConnectionBreakEvent event) {
    chatManager.disconnectChat(event.getChatId(), event.getSenderUsername());
  }

  private void handleChatMessageEvent(ChatMessageEvent event) {
    Path filePath = null;
    if (event.getAttachedFileName() != null) {
      try {
        filePath = Files.createTempFile(
            Path.of("/home/remi/Code"), event.getAttachedFileName() + "_", "");
      } catch (IOException e) {
        log.error("FAILED TO HANDLE MESSAGE DUE TO FILE CREATING ERROR");
        return;
      }
    }

    SymmetricCryptoContext cryptoContext = chatManager.getChatCryptoContext(event.getChatId());

    String text = new String(cryptoContext.decrypt(event.getMessageData()));
    Message message = new Message(event.getMessageId(), text,
        event.getSenderUsername(), filePath, false);
    MessageEntry messageEntry = chatManager.insertMessage(event.getChatId(), message);

    if (messageEntry == null) {
      return;
    }

    if (filePath == null) {
      messageEntry.complete();
    } else {
      Path loadPath = Path.of(filePath + "_encrypted");
      HttpMultipartProgress httpProgress = new HttpMultipartProgress();
      messageEntry.startTransmission(httpProgress);
      currentLoads.put(event.getMessageId(),
          new MessageLoadBundle(messageEntry, httpProgress, loadPath));
    }
  }

  void handleChatFileEvent(ChatFileEvent event) {
    MessageLoadBundle bundle = currentLoads.getOrDefault(event.getMessageId(), null);
    if (bundle == null) {
      log.error("Got unexpected file part");
      return;
    }

    if (bundle.getHttpProgress().getProgress() == 0) {
      bundle.getHttpProgress().setRequestsCount(event.getPartCount());
    }

    try (FileChannel fileChannel = FileChannel.open(bundle.loadPath, CREATE, WRITE)) {
      long pos = FILE_PART_SIZE * event.getPartNumber();
      int write = fileChannel.write(ByteBuffer.wrap(event.getFileData()), pos);
      if (write != event.getFileData().length) {
        log.error("Wrong written byte count");
      }
    } catch (IOException ex) {
      log.error("File writing error", ex);
      bundle.getMessageEntry().fail(); // TODO: remove bundle
    }

    bundle.getHttpProgress().incrementProcessedBlocksCount();

    if (bundle.getHttpProgress().isDone()) {
      SymmetricCryptoContext cryptoContext = chatManager.getChatCryptoContext(event.getChatId());
      String in = bundle.loadPath.toString();
      String out = bundle.messageEntry.getMessage().getFilePath().toString();
      CryptoProgress<Void> progress = cryptoContext.decryptAsync(in, out);
      bundle.messageEntry.startDecrypting(progress);
      progress.getFuture().thenWeaklyRunAsync(() -> FxUtil.runOnFxThread(bundle.messageEntry::complete));
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

  private void throwTransportIfStatusNotOk(int status) {
    if (status != 200) {
      throw new ChainTransportIntException(status);
    }
  }

  @Getter
  private static class MessageLoadBundle {

    private final MessageEntry messageEntry;
    private final HttpMultipartProgress httpProgress;
    private final Path loadPath;

    public MessageLoadBundle(MessageEntry messageEntry, HttpMultipartProgress httpProgress, Path loadPath) {
      this.messageEntry = messageEntry;
      this.httpProgress = httpProgress;
      this.loadPath = loadPath;
    }
  }
}
