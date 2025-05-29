package org.reminstant.secretalk.client.application;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import net.rgielen.fxweaver.core.FxmlView;
import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.secretalk.client.application.control.ExpandableTextArea;
import org.reminstant.secretalk.client.application.control.NotificationLabel;
import org.reminstant.secretalk.client.model.Chat;
import org.reminstant.secretalk.client.util.ClientStatus;
import org.reminstant.secretalk.client.util.FxUtil;
import org.reminstant.secretalk.client.util.StatusDescriptionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

@Component
@FxmlView("mainScene.fxml")
public class MainSceneController implements Initializable {

  private static final Logger log = LoggerFactory.getLogger(MainSceneController.class);

  private final ApplicationStateManager stateManager;
  private final StatusDescriptionHolder statusDescriptionHolder;

  @FXML private ImageView settingsButton;
  @FXML private ImageView addChatButton;
  @FXML private ImageView deleteChatButton;
  @FXML private ImageView attachFileButton;
  @FXML private ImageView sendButton;

  @FXML private ScrollPane chatHolderScroll;
  @FXML private VBox chatHolder;

  @FXML private VBox rightBlock;
  @FXML private Label chatTitle;

  @FXML private StackPane chatStateBlockHolder;
  @FXML private Button chatConnectionAcceptButton;
  @FXML private Button chatConnectionRequestButton;
  @FXML private Button chatConnectionBreakButton;
  @FXML private ScrollPane messageHolderWrapper;

  @FXML private AnchorPane chatFooter;
  @FXML private ExpandableTextArea messageInput;

  @FXML private HBox attachedFileBlock;
  @FXML private Label attachedFileLabel;
  @FXML private Label attachedFileCancelLabel;

  // "dialog" panes
  @FXML private Pane shadow;

  @FXML private VBox chatCreationBlock;
  @FXML private TextField chatCreationUsernameField;
  @FXML private TextField chatCreationTitleField;
  @FXML private ChoiceBox<String> chatCreationAlgoChoice;
  @FXML private ChoiceBox<String> chatCreationModeChoice;
  @FXML private ChoiceBox<String> chatCreationPaddingChoice;
  @FXML private NotificationLabel chatCreationNotificationLabel;
  @FXML private Button chatCreationButton;
  @FXML private Button chatCreationCancelButton;

  @FXML private VBox chatDeletionBlock;
  @FXML private Button chatSelfDeletionButton;
  @FXML private Button chatDeletionButton;
  @FXML private Button chatDeletionCancelButton;

  private final FileChooser messageFileChooser;
  private File attachedFile;

  private ChainableFuture<Void> lastCreationChatFuture = ChainableFuture.getCompleted();


  public MainSceneController(ApplicationStateManager sceneManager,
                             StatusDescriptionHolder statusDescriptionHolder) {
    this.stateManager = sceneManager;
    this.statusDescriptionHolder = statusDescriptionHolder;

    this.messageFileChooser = new FileChooser();
    this.attachedFile = null;
  }


  @SuppressWarnings("DuplicatedCode")
  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    chatHolder.prefWidthProperty().bind(chatHolderScroll.widthProperty().subtract(4));

    settingsButton.setOnMouseClicked(this::onSettingsButtonClicked);
    addChatButton.setOnMouseClicked(this::onAddChatButtonClicked);
    deleteChatButton.setOnMouseClicked(this::onDeleteChatButtonClicked);
    sendButton.setOnMouseClicked(this::onSendButtonClicked);
    attachFileButton.setOnMouseClicked(this::onAttachFileButtonClicked);

    attachedFileCancelLabel.setOnMouseClicked(this::onDetachFileButtonClicked);

    chatConnectionAcceptButton.setOnMouseClicked(this::onConnectionAcceptButtonClicked);
    chatConnectionRequestButton.setOnMouseClicked(this::onConnectionRequestButtonClicked);
    chatConnectionBreakButton.setOnMouseClicked(this::onConnectionBreakButtonClicked);

    shadow.setOnMouseClicked(this::onShadowClicked);
    chatCreationButton.setOnMouseClicked(this::onChatCreationButtonClicked);
    chatCreationButton.setOnKeyReleased(this::onChatCreationButtonKeyReleased);
    chatCreationCancelButton.setOnMouseClicked(this::onChatCreationCancelButtonClicked);
    chatCreationCancelButton.setOnKeyReleased(this::onChatCreationCancelButtonKeyReleased);
    chatSelfDeletionButton.setOnMouseClicked(this::onChatSelfDeletionButtonClicked);
    chatDeletionButton.setOnMouseClicked(this::onChatDeletionButtonClicked);
    chatDeletionCancelButton.setOnMouseClicked(this::onChatDeletionCancelButtonClicked);

    rightBlock.getChildren().forEach(node -> node.setVisible(false));

    Runnable onChatOpening = () -> rightBlock.getChildren().forEach(node -> node.setVisible(true));
    Runnable onChatClosing = () -> rightBlock.getChildren().forEach(node -> node.setVisible(false));
    Runnable onChatChanging = () -> {
      detachFile();
      messageInput.clear();
    };

    stateManager.initChatManager(
        chatHolder, chatTitle, chatStateBlockHolder, messageHolderWrapper, chatFooter,
        onChatOpening, onChatClosing, onChatChanging);
    log.info("MainSceneController INITIALIZED");
  }



  private void openChatCreationBlock() {
    shadow.setVisible(true);
    chatCreationBlock.setVisible(true);
  }

  private void closeChatCreationBlock() {
    shadow.setVisible(false);
    chatCreationBlock.setVisible(false);
    chatCreationUsernameField.setText("");
    chatCreationTitleField.setText("");
    chatCreationNotificationLabel.collapse();
    lastCreationChatFuture.cancel(true);
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

  private void attachFile(File file) {
    attachedFileLabel.setText(file.getName());
    if (attachedFile == null) {
      int pos = rightBlock.getChildren().size() - 1;
      rightBlock.getChildren().add(pos, attachedFileBlock);
    }
    attachedFile = file;
  }

  private void detachFile() {
    attachedFile = null;
    rightBlock.getChildren().remove(attachedFileBlock);
  }



  private void processChatDeserting() {
    stateManager.processActiveChatDesertion()
        .thenWeaklyConsumeAsync(status -> {
          if (status == ClientStatus.OK) {
            FxUtil.runOnFxThread(this::closeChatDeletionBlock);
          } else {
            // TODO: deletion error displaying
//            String desc = statusDescriptionHolder.getDescription(status, "creationChatStatus");
//            chatCreationNotificationLabel.showError(desc);
          }
        });
  }

  private void processChatDestroying() {
    stateManager.processActiveChatDestruction()
        .thenWeaklyConsumeAsync(status -> {
          if (status == ClientStatus.OK) {
            FxUtil.runOnFxThread(this::closeChatDeletionBlock);
          } else {
            // TODO: deletion error displaying
//            String desc = statusDescriptionHolder.getDescription(status, "creationChatStatus");
//            chatCreationNotificationLabel.showError(desc);
          }
        });
  }

  private void processChatCreation() {
    String otherUsername = chatCreationUsernameField.getText();
    String title = chatCreationTitleField.getText();
    String cryptoSystemName = chatCreationAlgoChoice.getValue();
    String cipherMode = chatCreationModeChoice.getValue();
    String paddingMode = chatCreationPaddingChoice.getValue();
    Chat.Configuration config = new Chat.Configuration(title, cryptoSystemName, cipherMode, paddingMode);

    chatCreationButton.setDisable(true);

    lastCreationChatFuture = stateManager.processChatCreation(otherUsername, config)
        .thenWeaklyConsumeAsync(status -> {
          if (status == ClientStatus.OK) {
            FxUtil.runOnFxThread(this::closeChatCreationBlock);
          } else {
            FxUtil.runOnFxThread(() -> {
              String desc = statusDescriptionHolder.getDescription(status, "creationChatStatus");
              chatCreationNotificationLabel.showError(desc);
            });
          }
          chatCreationButton.setDisable(false);
        });

    lastCreationChatFuture
        .thenWeaklyHandleAsync(_ -> {
          chatCreationButton.setDisable(false);
          return null;
        });
  }

  private void processActiveChatAcceptance() {
    stateManager.processActiveChatAcceptance()
        .thenWeaklyConsumeAsync(status -> {
          if (status != ClientStatus.OK) {
            log.error("Failed to accept chat connection");
          }
        });
  }

  private void processActiveChatRequest() {
    stateManager.processActiveChatRequest()
        .thenWeaklyConsumeAsync(status -> {
          if (status != ClientStatus.OK) {
            log.error("Failed to request chat connection");
          }
        });
  }

  private void processActiveChatDisconnection() {
    stateManager.processActiveChatDisconnection()
        .thenWeaklyConsumeAsync(status -> {
          if (status != ClientStatus.OK) {
            log.error("Failed to break chat connection");
          }
        });
  }

  private void processAttachingFile() {
    File file = messageFileChooser.showOpenDialog(shadow.getScene().getWindow());
    if (file != null) {
      attachFile(file);
    }
  }

  private void processSendingMessage() {
    String messageText = messageInput.getText();
    File messageFile = attachedFile;
    if (messageText.isEmpty() && messageFile == null) {
      return;
    }

    messageInput.clear();
    detachFile();

    stateManager.processSendingMessage(messageText, messageFile);
//        .thenWeaklyConsumeAsync(status -> {
//          if (status == ClientStatus.OK) {
//            FxUtil.runOnFxThread(() -> {
//              messageInput.clear();
////              ConcurrentUtil.sleepSafely(100);
//            });
//          } else {
//            // TODO: handle
//          }
//        });

  }



  private void onSettingsButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      stateManager.processLogout();
    }
  }

  private void onAddChatButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      openChatCreationBlock();
    }
  }

  private void onDeleteChatButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      openChatDeletionBlock();
    }
  }

  private void onShadowClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      closeChatCreationBlock();
      closeChatDeletionBlock();
    }
  }

  private void onChatCreationButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processChatCreation();
    }
  }

  private void onChatCreationButtonKeyReleased(KeyEvent e) {
    if (e.getCode().equals(KeyCode.ENTER)) {
      processChatCreation();
    }
  }

  private void onChatCreationCancelButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      closeChatCreationBlock();
    }
  }

  private void onChatCreationCancelButtonKeyReleased(KeyEvent e) {
    if (e.getCode().equals(KeyCode.ENTER)) {
      closeChatCreationBlock();
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

  private void onAttachFileButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processAttachingFile();
    }
  }

  private void onDetachFileButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      detachFile();
    }
  }

  private void onConnectionAcceptButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processActiveChatAcceptance();
    }
  }

  private void onConnectionRequestButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processActiveChatRequest();
    }
  }

  private void onConnectionBreakButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processActiveChatDisconnection();
    }
  }

  private void onSendButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processSendingMessage();
    }
  }
}
