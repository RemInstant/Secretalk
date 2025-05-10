package org.reminstant.cryptomessengerclient.application;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import net.rgielen.fxweaver.core.FxmlView;
import org.reminstant.concurrent.ConcurrentUtil;
import org.reminstant.cryptomessengerclient.application.control.ExpandableTextArea;
import org.reminstant.cryptomessengerclient.application.control.NotificationLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

@Component
@FxmlView("mainScene.fxml")
public class MainSceneController implements Initializable {

  private static final Logger log = LoggerFactory.getLogger(MainSceneController.class);

  private final ApplicationStateManager stateManager;
  private final StatusDescriptionHolder statusDescriptionHolder;

  public MainSceneController(ApplicationStateManager sceneManager,
                             StatusDescriptionHolder statusDescriptionHolder) {
    this.stateManager = sceneManager;
    this.statusDescriptionHolder = statusDescriptionHolder;
  }

  @FXML
  private ImageView settingsButton;
  @FXML
  private ImageView addChatButton;
  @FXML
  private ImageView deleteChatButton;
  @FXML
  private ImageView sendButton;

  @FXML
  private ScrollPane chatHolderScroll;
  @FXML
  private VBox chatHolder;
  @FXML
  private ExpandableTextArea messageInput;


  @FXML
  private VBox rightBlock;
  @FXML
  private Label chatTitle;
  @FXML
  private StackPane chatStateBlockHolder;

  @FXML
  private ScrollPane messageHolderWrapper;

  // "dialog" panes
  @FXML
  private Pane shadow;

  @FXML
  private VBox chatCreationBlock;
  @FXML
  private TextField chatCreatingUsernameField;
  @FXML
  private NotificationLabel chatCreationNotificationLabel;
  @FXML
  private Button chatCreatingButton;
  @FXML
  private Button chatCreatingCancelButton;

  @FXML
  private VBox chatDeletionBlock;
  @FXML
  private Button chatSelfDeletionButton;
  @FXML
  private Button chatDeletionButton;
  @FXML
  private Button chatDeletionCancelButton;


  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    chatHolder.prefWidthProperty().bind(chatHolderScroll.widthProperty().subtract(4));

    settingsButton.setOnMouseClicked(this::onSettingsButtonClicked);
    addChatButton.setOnMouseClicked(this::onAddChatButtonClicked);
    deleteChatButton.setOnMouseClicked(this::onAddDeleteButtonClicked);
    sendButton.setOnMouseClicked(this::onSendMessageButtonClicked);

    shadow.setOnMouseClicked(this::onShadowClicked);
    chatCreatingButton.setOnMouseClicked(this::onChatCreatingButtonClicked);
    chatCreatingCancelButton.setOnMouseClicked(this::onChatCreatingCancelButtonClicked);
    chatSelfDeletionButton.setOnMouseClicked(this::onChatSelfDeletionButtonClicked);
    chatDeletionButton.setOnMouseClicked(this::onChatDeletionButtonClicked);
    chatDeletionCancelButton.setOnMouseClicked(this::onChatDeletionCancelButtonClicked);

    rightBlock.getChildren().forEach(node -> node.setVisible(false));

    Runnable onOpening = () -> rightBlock.getChildren().forEach(node -> node.setVisible(true));
    Runnable onClosing = () -> rightBlock.getChildren().forEach(node -> node.setVisible(false));

    stateManager.initChatManager(chatHolder, chatTitle, chatStateBlockHolder, messageHolderWrapper,
        onOpening, onClosing);
    log.info("MainSceneController INITIALIZED");
  }



  private void openChatCreatingBlock() {
    shadow.setVisible(true);
    chatCreationBlock.setVisible(true);
  }

  private void closeChatCreatingBlock() {
    shadow.setVisible(false);
    chatCreationBlock.setVisible(false);
    chatCreatingUsernameField.setText("");
    chatCreationNotificationLabel.collapse();
  }

  private void openChatDeletionBlock() {
    shadow.setVisible(true);
    chatDeletionBlock.setVisible(true);
  }

  private void closeChatDeletionBlock() {
    shadow.setVisible(false);
    chatDeletionBlock.setVisible(false);
//    chatCreationNotificationLabel.collapse();
  }

  private void processChatDeserting() {
    stateManager.processChatDeserting()
        .thenWeaklyConsumeAsync(status -> {
          if (status == 200) {
            FxUtil.runOnFxThread(this::closeChatDeletionBlock);
          } else {
            // TODO: deletion error displaying
//            String desc = statusDescriptionHolder.getDescription(status, "creatingChatStatus");
//            chatCreationNotificationLabel.showError(desc);
          }
        });
  }

  private void processChatDestroying() {
    stateManager.processChatDestroying()
        .thenWeaklyConsumeAsync(status -> {
          if (status == 200) {
            FxUtil.runOnFxThread(this::closeChatDeletionBlock);
          } else {
            // TODO: deletion error displaying
//            String desc = statusDescriptionHolder.getDescription(status, "creatingChatStatus");
//            chatCreationNotificationLabel.showError(desc);
          }
        });
  }

  private void processChatCreating() {
    String otherUsername = chatCreatingUsernameField.getText();

    stateManager.processChatCreation(otherUsername)
        .thenWeaklyConsumeAsync(status -> {
          if (status == 200) {
            FxUtil.runOnFxThread(this::closeChatCreatingBlock);
          } else {
            FxUtil.runOnFxThread(() -> {
              String desc = statusDescriptionHolder.getDescription(status, "creatingChatStatus");
              chatCreationNotificationLabel.showError(desc);
            });
          }
        });
  }

  private void processSendingMessage() {
    String messageText = messageInput.getText();
    if (messageText == null || messageText.isEmpty()) {
      return;
    }

    stateManager.processSendingMessage(messageText)
        .thenWeaklyConsumeAsync(status -> {
          if (status == 200) {
            FxUtil.runOnFxThread(() -> {
              messageInput.clear();
              ConcurrentUtil.sleepSafely(100);
            });
          } else {
            // TODO: handle
          }
        });
  }



  private void onSettingsButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      stateManager.processLogout();
    }
  }

  private void onAddChatButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      openChatCreatingBlock();
    }
  }

  private void onAddDeleteButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      openChatDeletionBlock();
    }
  }

  private void onShadowClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      closeChatCreatingBlock();
      closeChatDeletionBlock();
    }
  }

  private void onChatCreatingButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processChatCreating();
    }
  }

  private void onChatCreatingCancelButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      closeChatCreatingBlock();
    }
  }

  private void onChatSelfDeletionButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processChatDeserting();
    }
  }

  private void onChatDeletionButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processChatDestroying();
    }
  }

  private void onChatDeletionCancelButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      closeChatDeletionBlock();
    }
  }

  private void onSendMessageButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processSendingMessage();
    }
  }
}
