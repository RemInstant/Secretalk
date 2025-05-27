package org.reminstant.secretalk.client.application;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import net.rgielen.fxweaver.core.FxmlView;
import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.secretalk.client.application.control.ExpandableTextArea;
import org.reminstant.secretalk.client.application.control.NotificationLabel;
import org.reminstant.secretalk.client.model.Chat;
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
  @FXML private ExpandableTextArea messageInput;

  @FXML private HBox attachedFileBlock;
  @FXML private Label attachedFileLabel;
  @FXML private Label attachedFileCancelLabel;

  // "dialog" panes
  @FXML private Pane shadow;

  @FXML private VBox chatCreationBlock;
  @FXML private TextField chatCreatingUsernameField;
  @FXML private TextField chatCreatingTitleField;
  @FXML private ChoiceBox<String> chatCreatingAlgoChoice;
  @FXML private ChoiceBox<String> chatCreatingModeChoice;
  @FXML private ChoiceBox<String> chatCreatingPaddingChoice;
  @FXML private NotificationLabel chatCreationNotificationLabel;
  @FXML private Button chatCreatingButton;
  @FXML private Button chatCreatingCancelButton;

  @FXML private VBox chatDeletionBlock;
  @FXML private Button chatSelfDeletionButton;
  @FXML private Button chatDeletionButton;
  @FXML private Button chatDeletionCancelButton;

  private final FileChooser messageFileChooser;
  private File attachedFile;


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
//    chatCreatingUsernameField.textProperty().addListener(_ ->
//        chatCreatingTitleField.setText(chatCreatingUsernameField.getText()));

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
    chatCreatingButton.setOnMouseClicked(this::onChatCreatingButtonClicked);
    chatCreatingCancelButton.setOnMouseClicked(this::onChatCreatingCancelButtonClicked);
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
        chatHolder, chatTitle, chatStateBlockHolder, messageHolderWrapper,
        onChatOpening, onChatClosing, onChatChanging);
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
    chatCreatingTitleField.setText("");
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
    stateManager.processActiveChatDestruction()
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
    String title = chatCreatingTitleField.getText();
    String cryptoSystemName = chatCreatingAlgoChoice.getValue();
    String cipherMode = chatCreatingModeChoice.getValue();
    String paddingMode = chatCreatingPaddingChoice.getValue();
    Chat.Configuration config = new Chat.Configuration(title, cryptoSystemName, cipherMode, paddingMode);

    stateManager.processChatCreation(otherUsername, config)
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

  private void processActiveChatAcceptance() {
    stateManager.processActiveChatAcceptance()
        .thenWeaklyConsumeAsync(status -> {
          if (status != 200) {
            log.error("Failed to accept chat");
          }
        });
  }

  private void processActiveChatRequest() {
    stateManager.processActiveChatRequest()
        .thenWeaklyConsumeAsync(status -> {
          if (status != 200) {
            log.error("Failed to accept chat");
          }
        });
  }

  private void processActiveChatDisconnection() {
    stateManager.processActiveChatDisconnection()
        .thenWeaklyConsumeAsync(status -> {
          if (status != 200) {
            log.error("Failed to accept chat");
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
//          if (status == 200) {
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
      openChatCreatingBlock();
    }
  }

  private void onDeleteChatButtonClicked(MouseEvent e) {
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
