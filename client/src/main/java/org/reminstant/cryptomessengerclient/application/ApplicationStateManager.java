package org.reminstant.cryptomessengerclient.application;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import net.rgielen.fxweaver.core.FxWeaver;
import org.reminstant.concurrent.ConcurrentUtil;
import org.reminstant.cryptomessengerclient.component.DiffieHellmanGenerator;
import org.reminstant.cryptomessengerclient.component.ServerClient;
import org.reminstant.cryptomessengerclient.dto.DHResponse;
import org.reminstant.cryptomessengerclient.dto.JwtResponse;
import org.reminstant.cryptomessengerclient.dto.NoPayloadResponse;
import org.reminstant.cryptomessengerclient.dto.UserEventWrapperResponse;
import org.reminstant.cryptomessengerclient.exception.InvalidServerAnswer;
import org.reminstant.cryptomessengerclient.model.SecretChat;
import org.reminstant.cryptomessengerclient.model.event.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

@Slf4j
@Component
public class ApplicationStateManager {

  private static final long EVENT_CYCLE_TIMEOUT = 30000;
  private static final int DH_PRIVATE_KEY_BIT_LENGTH = 512;
  private static final String CONNECTION_FAILURE_LOG = "Failed to connect to the http server";
  private static final String INVALID_SERVER_ANSWER_LOG = "Got invalid server answer";
  private static final String DIFFIE_HELLMAN_NOT_INIT_LOG = "DiffieHellmanGenerator is uninitialised";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final FxWeaver fxWeaver;
  private final ServerClient serverClient;
  private final ChatManager chatManager;

  private Stage stage = null;
  private Scene loginScene = null;
  private Scene mainScene = null;

  private final AtomicBoolean isEventCycleWorking = new AtomicBoolean(false);
  private DiffieHellmanGenerator dh = null;

  public ApplicationStateManager(FxWeaver fxWeaver,
                                 ServerClient serverClient,
                                 ChatManager chatManager) {
    this.fxWeaver = fxWeaver;
    this.serverClient = serverClient;
    this.chatManager = chatManager;
  }

  public void init(Stage stage) {
    this.stage = stage;
    stage.setTitle("123");
//    showLoginScene();
    showMainScene(); // NOSONAR
    chatManager.loadChats(); // NOSONAR
  }

  public void initChatManager(Pane chatHolder, Label chatTitle,
                              StackPane chatStateBlockHolder, ScrollPane messageHolderWrapper,
                              Runnable onChatOpening, Runnable onChatClosing) {
    ToIntFunction<SecretChat> onChatRequest = chat -> processChatRequest(chat.getId(), chat.getTitle());
    ToIntFunction<SecretChat> onChatAccept = chat -> processChatAcceptance(chat.getId(), chat.getTitle());
    ToIntFunction<SecretChat> onChatDisconnect = chat -> processChatDisconnect(chat.getId(), chat.getTitle());

    chatManager.initObjects(chatHolder, chatTitle, chatStateBlockHolder, messageHolderWrapper);
    chatManager.initBehaviour(onChatOpening, onChatClosing, onChatRequest, onChatAccept, onChatDisconnect);
    log.info("ChatManager INITIALIZED");
  }

  public int processLogin(String username, String password) {
    JwtResponse jwtResponse;
    DHResponse dhResponse;
    try {
      jwtResponse = serverClient.processLogin(username, password);
      if (jwtResponse.getStatus() != 200) {
        return jwtResponse.getStatus();
      }
      dhResponse = serverClient.getDHParams();
      if (dhResponse.getStatus() != 200) {
        return dhResponse.getStatus();
      }
    } catch (IOException ex) {
      log.error(CONNECTION_FAILURE_LOG, ex);
      return 0;
    } catch (InvalidServerAnswer ex) {
      log.error(INVALID_SERVER_ANSWER_LOG, ex);
      return 602;
    }

    log.info("LOGGED AS {}", username);

    dh = new DiffieHellmanGenerator(dhResponse.getPrime(), dhResponse.getGenerator());
    serverClient.saveCredentials(username, jwtResponse.getToken());
    showMainScene();
    chatManager.loadChats();
    startEventCycle();

    return 200;
  }

  public int processRegister(String username, String password) {
    try {
      NoPayloadResponse noPayloadResponse = serverClient.processRegister(username, password);
      return noPayloadResponse.getStatus();
    } catch (IOException ex) {
      log.error(CONNECTION_FAILURE_LOG, ex);
      return 0;
    } catch (InvalidServerAnswer ex) {
      log.error(INVALID_SERVER_ANSWER_LOG, ex);
      return 602;
    }
  }

  public void processLogout() {
    stopEventCycle();
    serverClient.eraseCredentials();
    chatManager.reset();
    log.info("LOG OUT");
    showLoginScene();
  }

  public int processChatCreation(String otherUsername) {
    String chatId = UUID.randomUUID().toString();
    return processChatRequest(chatId, otherUsername);
  }

  public int processChatDeserting() {
    String chatId = chatManager.getActiveChatId();
    String otherUsername = chatManager.getActiveChatOtherUsername();
    boolean isActiveChatAboutToDelete = chatManager.isActiveChatAboutToDelete();
    if (chatId == null || otherUsername == null) {
      log.error("Tried to desert chat when there no active chats");
      return 601;
    }

    if (!isActiveChatAboutToDelete) {
      try {
        NoPayloadResponse response = serverClient.desertChat(chatId, otherUsername);
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
    }

    chatManager.deleteChat(chatId);
    return 200;
  }

  public int processChatDestroying() {
    String chatId = chatManager.getActiveChatId();
    String otherUsername = chatManager.getActiveChatOtherUsername();
    boolean isActiveChatAboutToDelete = chatManager.isActiveChatAboutToDelete();
    if (chatId == null || otherUsername == null) {
      log.error("Tried to destroy chat when there no active chats");
      return 601;
    }

    if (!isActiveChatAboutToDelete) {
      try {
        NoPayloadResponse response = serverClient.destroyChat(chatId, otherUsername);
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
    }

    chatManager.deleteChat(chatId);
    return 200;
  }

  public int processChatRequest(String chatId, String otherUsername) {
    if (dh == null) {
      log.error(DIFFIE_HELLMAN_NOT_INIT_LOG);
      return 601;
    }
    BigInteger privateKey = dh.generatePrivateKey(DH_PRIVATE_KEY_BIT_LENGTH);
    BigInteger publicKey = dh.generatePublicKey(privateKey);

    try {
      NoPayloadResponse response = serverClient
          .requestChatConnection(chatId, otherUsername, publicKey.toString());
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

    chatManager.createOrReconnectOnRequesterSide(chatId, otherUsername, privateKey);
    return 200;
  }

  public int processChatAcceptance(String chatId, String otherUsername) {
    if (dh == null) {
      log.error(DIFFIE_HELLMAN_NOT_INIT_LOG);
      return 601;
    }
    BigInteger privateKey = dh.generatePrivateKey(DH_PRIVATE_KEY_BIT_LENGTH);
    BigInteger publicKey = dh.generatePublicKey(privateKey);

    try {
      NoPayloadResponse response = serverClient
          .acceptChatConnection(chatId, otherUsername, publicKey.toString());
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

    UnaryOperator<BigInteger> generator = otherPublicKey -> dh.generateSessionKey(privateKey, otherPublicKey);
    chatManager.acceptChat(chatId, otherUsername, generator);
    return 200;
  }

  public int processChatDisconnect(String chatId, String otherUsername) {
    try {
      NoPayloadResponse response = serverClient.breakChatConnection(chatId, otherUsername);
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

    chatManager.disconnectChat(chatId, otherUsername);
    return 200;
  }



  private void startEventCycle() {
    isEventCycleWorking.set(true);
    CompletableFuture.runAsync(() -> {
      while (isEventCycleWorking.get()) {
        boolean isOk = doEventCycle();
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
      Map<String, String> data = objectMapper.readValue(wrapper.getEventJson(), new TypeReference<>() {});
      rawEvent = UserEvent.getEvent(wrapper.getEventType(), data);
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
    String chatTitle = event.getRequesterUsername();
    BigInteger publicKey = new BigInteger(event.getPublicKey());
    chatManager.createOrReconnectOnAcceptorSide(chatId, chatTitle, publicKey);
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


  private void showLoginScene() {
    Objects.requireNonNull(stage, "Cannot show login scene because stage is uninitialised");

    if (loginScene == null) {
      Parent root = fxWeaver.loadView(LoginSceneController.class);
      loginScene = new Scene(root);
    }

    stage.setScene(loginScene);
    stage.setResizable(false);
    stage.show();
    centerStage();
  }

  private void showMainScene() {
    Objects.requireNonNull(stage, "Cannot show main scene because stage is uninitialised");

    if (mainScene == null) {
      Parent root = fxWeaver.loadView(MainSceneController.class);
      mainScene = new Scene(root);
    }

    stage.setScene(mainScene);
    stage.setMinWidth(460.0);
    stage.setMinHeight(300.0);
    stage.setResizable(true);
    stage.show();
    centerStage();
  }

  private void centerStage() {
    Rectangle2D primScreenBounds = Screen.getPrimary().getVisualBounds();
    stage.setX((primScreenBounds.getWidth() - stage.getWidth()) / 2);
    stage.setY((primScreenBounds.getHeight() - stage.getHeight()) / 2);
  }
}
