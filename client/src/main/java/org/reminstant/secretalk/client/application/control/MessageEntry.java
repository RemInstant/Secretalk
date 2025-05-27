package org.reminstant.secretalk.client.application.control;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.concurrent.Progress;
import org.reminstant.cryptography.context.CryptoProgress;
import org.reminstant.secretalk.client.SpringBootWrapperApplication;
import org.reminstant.secretalk.client.model.Message;
import org.reminstant.secretalk.client.util.FxUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MessageEntry extends HBox {

  private final HostServices hostServices;
  @Getter
  private final Message message;
  @Getter
  private final ObjectProperty<Message.State> stateProperty;
  private final Set<Progress<?>> progresses;

  private final BooleanProperty fileImageRequesting;
  private final BooleanProperty fileImageLoading;
  private final BooleanProperty fileImageCompleted;
  private final BooleanProperty fileImageFailed;
  
  private final VBox messageBlock;
  private final Label messageAuthor;
  private HBox fileBlock;
  private ImageView fileImage;
  private Label fileLabel;
  private HBox progressBlock;
  private ProgressBar progressBar;
  private Label stateLabel;
  private Label cancelLabel;
  @Setter
  private Runnable onCancel;
  @Setter
  private Runnable onFileRequest;


  public MessageEntry(Message message, HostServices hostServices) {
    super();
    this.hostServices = hostServices;
    this.message = message;
    this.stateProperty = new SimpleObjectProperty<>(message.getState());
    this.progresses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    this.fileImageRequesting = new SimpleBooleanProperty(false);
    this.fileImageLoading = new SimpleBooleanProperty(false);
    this.fileImageCompleted = new SimpleBooleanProperty(false);
    this.fileImageFailed = new SimpleBooleanProperty(false);

    stateProperty.addListener(_ -> message.setState(stateProperty.get()));
    getStyleClass().setAll("messageEntry");

    // init common objects
    this.messageBlock = new VBox();
    this.messageAuthor = new Label(message.getAuthor());
    Label messageText = new Label(message.getText());

    if (!message.getText().isEmpty()) {
      messageBlock.getChildren().add(messageText);
    }

    messageBlock.getStyleClass().add("messageBlock");
    messageAuthor.getStyleClass().add("messageAuthor");
    messageText.getStyleClass().add("messageText");

    if (message.getFileName() != null) {
      String iconPath = message.getFilePath() != null
          ? "icons/filled/fileIcon32x32.png"
          : "icons/filled/downloadIcon32x32.png";
      String url = Objects.requireNonNull(SpringBootWrapperApplication.class
          .getResource(iconPath)).toString();
      String fileName = message.getFileName();
      fileImage = new ImageView(url);
      fileLabel = new Label(fileName);
      Pane filler = new Pane();
      fileBlock = new HBox(fileImage, fileLabel, filler);
      messageBlock.getChildren().add(fileBlock);

      fileBlock.getStyleClass().add("messageFileBlock");
      fileImage.getStyleClass().add("messageFileImage");
      fileLabel.getStyleClass().add("messageFileLabel");

      fileLabel.setTooltip(new Tooltip(fileName));
      HBox.setHgrow(filler, Priority.ALWAYS);

      addPseudoClassListener(fileImage, fileImageRequesting, "requesting");
      addPseudoClassListener(fileImage, fileImageLoading, "loading");
      addPseudoClassListener(fileImage, fileImageCompleted, "completed");
      addPseudoClassListener(fileImage, fileImageFailed, "failed");

      fileImage.setOnMouseClicked(this::onFileImageClicked);
    }

    messageBlock.getChildren().add(messageAuthor);

    Pane filler = new Pane();
    HBox.setHgrow(filler, Priority.ALWAYS);

    if (message.isBelongedToReceiver()) {
      this.getChildren().addAll(filler, messageBlock);
    } else {
      this.getChildren().addAll(messageBlock, filler);
    }

    switch (message.getState()) {
      case REQUESTING -> startRequesting();
      case FAILED -> fail();
      case SENT -> complete();
      default -> {} // NOSONAR
    }
  }

  public void startEncryption(Progress<?> progress, boolean showProgressBar) {
    Objects.requireNonNull(progress, "progress cannot be null");
    stateProperty.setValue(Message.State.ENCRYPTING);
    trackProgress(progress);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(showProgressBar);
      stateLabel.setText("шифрование");
    });
  }

  public void startUploading(Progress<?> progress, boolean showProgressBar) {
    Objects.requireNonNull(progress, "progress cannot be null");
    stateProperty.setValue(Message.State.UPLOADING);
    trackProgress(progress);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(showProgressBar);
      stateLabel.setText("отправка");
    });
  }

  public void startRequesting() {
    setFileImageRequesting();
    stateProperty.setValue(Message.State.REQUESTING);
    hideProgressBlock();
  }

  public void startDownloading(Progress<?> progress) {
    Objects.requireNonNull(progress, "progress cannot be null");

    String iconPath = "icons/filled/fileIcon32x32.png";
    String url = Objects.requireNonNull(SpringBootWrapperApplication.class
        .getResource(iconPath)).toString();
    fileImage.setImage(new Image(url));

    setFileImageLoading();
    stateProperty.setValue(Message.State.DOWNLOADING);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(true);
      stateLabel.setText("загрузка");
      trackProgress(progress);
    });
  }

  public void startDecryption(CryptoProgress<?> progress) {
    Objects.requireNonNull(progress, "progress cannot be null");
    stateProperty.setValue(Message.State.DECRYPTING);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(true);
      stateLabel.setText("расшифровка");
      trackProgress(progress);
    });
  }

  public void fail() {
    cancelTasks();
    setFileImageFailed();
    stateProperty.setValue(Message.State.FAILED);

    String iconPath = "icons/filled/errorIcon32x32.png";
    String url = Objects.requireNonNull(SpringBootWrapperApplication.class
        .getResource(iconPath)).toString();
    fileImage.setImage(new Image(url));

    showProgressBlock(false);
    progressBar.setVisible(false);
    stateLabel.setText("ОШИБКА");
  }

  public void cancel() {
    cancelTasks();
    if (message.isBelongedToReceiver()) {
      stateProperty.setValue(Message.State.CANCELLED);
    } else {
      stateProperty.setValue(Message.State.SENT);
      messageBlock.getChildren().remove(fileBlock);
      hideProgressBlock();
    }
    if (onCancel != null) {
      onCancel.run();
    }
  }

  public void complete() {
    setFileImageCompleted();
    stateProperty.setValue(Message.State.SENT);
    hideProgressBlock();
  }



  private void onFileImageClicked(MouseEvent event) {
    if (message.getFileName() == null || fileImageLoading.get() || fileImageFailed.get()) {
      return;
    }
    if (event.getButton().equals(MouseButton.PRIMARY)) {
      if (fileImageRequesting.get() && onFileRequest != null) {
        onFileRequest.run();
      }
      if (fileImageCompleted.get()) {
        Path path = message.getFilePath();
        if (event.isControlDown()) {
          path = path.getParent();
        }
        if (!Files.exists(path)) {
          log.error("File '{}' does not exist", path);
          // TODO: failure icon
          fail();
          return;
        }
//      try {
//        Runtime.getRuntime().exec("nautilus --select "+path);
//      } catch (IOException ex) {
//        log.error("", ex);
//      }

        hostServices.showDocument("file:///" + path);
      }
    }
  }

  private void showProgressBlock(boolean isProgressBarVisible) {
    messageBlock.getChildren().remove(messageAuthor);
    if (progressBlock == null) {
      progressBar = new ProgressBar(0);
      stateLabel = new Label("подготовка");
      cancelLabel = new Label("x");
      progressBlock = new HBox(cancelLabel, progressBar, stateLabel);

      progressBar.getStyleClass().add("messageProgressBar");
      stateLabel.getStyleClass().add("stateLabel");
      cancelLabel.getStyleClass().add("messageCancelLabel");
      progressBlock.getStyleClass().add("messageProgressBlock");

      HBox.setHgrow(progressBar, Priority.ALWAYS);
      cancelLabel.setOnMouseClicked(e -> {
        if (e.getButton().equals(MouseButton.PRIMARY)) {
          this.cancel();
        }
      });
    }
    if (!messageBlock.getChildren().contains(progressBlock)) {
      messageBlock.getChildren().add(progressBlock);
    }
    progressBar.setVisible(isProgressBarVisible);
  }

  private void hideProgressBlock() {
    messageBlock.getChildren().remove(progressBlock);
    if (!messageBlock.getChildren().contains(messageAuthor)) {
      messageBlock.getChildren().add(messageAuthor);
    }
    progressBar = null;
    stateLabel = null;
    cancelLabel = null;
    progressBlock = null;
  }

  private void trackProgress(Progress<?> progress) {
    progresses.add(progress);
    ChainableFuture.runStronglyAsync(() -> {
      while (!progress.isDone()) {
        Platform.runLater(() -> setProgress(progress.getProgress()));
        Thread.sleep(200);
      }
      Platform.runLater(() -> setProgress(progress.getProgress()));
      if (progress.isCompletedExceptionally()) {
        FxUtil.runOnFxThread(this::fail);
      }
      progresses.remove(progress);
    });
  }

  private void setProgress(double value) {
    if (progressBar != null) {
      progressBar.setProgress(value);
    }
  }

  private void cancelTasks() {
    progresses.forEach(progress -> progress.cancel(true));
  }

  private void setFileImageRequesting() {
    if (message.isBelongedToReceiver()) {
      setFileImageCompleted();
      return;
    }
    fileImageRequesting.setValue(true);
    fileImageLoading.setValue(false);
    fileImageCompleted.setValue(false);
    fileImageFailed.setValue(false);
  }

  private void setFileImageLoading() {
    if (message.isBelongedToReceiver()) {
      setFileImageCompleted();
      return;
    }
    fileImageRequesting.setValue(false);
    fileImageLoading.setValue(true);
    fileImageCompleted.setValue(false);
    fileImageFailed.setValue(false);
  }

  private void setFileImageCompleted() {
    fileImageRequesting.setValue(false);
    fileImageLoading.setValue(false);
    fileImageCompleted.setValue(true);
    fileImageFailed.setValue(false);
  }

  private void setFileImageFailed() {
    if (message.isBelongedToReceiver()) {
      setFileImageCompleted();
      return;
    }
    fileImageRequesting.setValue(false);
    fileImageLoading.setValue(false);
    fileImageCompleted.setValue(false);
    fileImageFailed.setValue(true);
  }

  private void addPseudoClassListener(Node node, BooleanProperty property, String pseudoClassName) {
    property.addListener(_ -> node.pseudoClassStateChanged(
        PseudoClass.getPseudoClass(pseudoClassName), property.get()));
  }
}
