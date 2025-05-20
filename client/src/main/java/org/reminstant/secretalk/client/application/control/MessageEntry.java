package org.reminstant.secretalk.client.application.control;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.cryptography.context.CryptoProgress;
import org.reminstant.secretalk.client.SpringBootWrapperApplication;
import org.reminstant.secretalk.client.application.HttpMultipartProgress;
import org.reminstant.secretalk.client.model.Message;
import org.reminstant.secretalk.client.util.FxUtil;

import java.util.Objects;

public class MessageEntry extends HBox {

  @Getter
  private final Message message;

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

  public MessageEntry(Message message) {
    super();
    this.message = message;
    getStyleClass().setAll("messageEntry");

    // init common objects
    this.messageBlock = new VBox();
    this.messageAuthor = new Label(message.getAuthor());
    Label messageText = new Label(message.getText());
    messageBlock.getChildren().add(messageText);

    messageBlock.getStyleClass().add("messageBlock");
    messageAuthor.getStyleClass().add("messageAuthor");
    messageText.getStyleClass().add("messageText");

    if (message.getFilePath() != null) {
      String url = Objects.requireNonNull(SpringBootWrapperApplication.class
          .getResource("icons/filled/fileIcon32x32.png")).toString();
      fileImage = new ImageView(url);
      fileLabel = new Label(message.getFilePath().getFileName().toString());
      fileBlock = new HBox(fileImage, fileLabel);
      messageBlock.getChildren().add(fileBlock);
    }

    messageBlock.getChildren().add(messageAuthor);

    Pane filler = new Pane();
    HBox.setHgrow(filler, Priority.ALWAYS);

    if (message.isBelongedToReceiver()) {
      this.getChildren().addAll(filler, messageBlock);
    } else {
      this.getChildren().addAll(messageBlock, filler);
    }
  }



  public void startEncrypting() {
    message.setState(Message.State.ENCRYPTING);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(false);
      stateLabel.setText("шифрование");
    });
  }

  public void startEncrypting(CryptoProgress<?> progress) {
    Objects.requireNonNull(progress, "progress cannot be null");
    message.setState(Message.State.ENCRYPTING);
    trackCryptoProgress(progress);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(true);
      stateLabel.setText("шифрование");
    });
  }

  public void startTransmission() {
    message.setState(Message.State.TRANSMITTING);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(false);
      stateLabel.setText("передача");
    });
  }

  public void startTransmission(HttpMultipartProgress progress) {
    Objects.requireNonNull(progress, "progress cannot be null");
    message.setState(Message.State.TRANSMITTING);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(true);
      stateLabel.setText("передача");
      trackHttpMultipartProgress(progress);
    });
  }

  public void startDecrypting() {
    message.setState(Message.State.TRANSMITTING);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(false);
      stateLabel.setText("расшифровка");
    });
  }

  public void startDecrypting(CryptoProgress<?> progress) {
    Objects.requireNonNull(progress, "progress cannot be null");
    message.setState(Message.State.TRANSMITTING);
    FxUtil.runOnFxThread(() -> {
      showProgressBlock(true);
      stateLabel.setText("расшифровка");
      trackCryptoProgress(progress);
    });
  }

  public void fail() {
    message.setState(Message.State.FAILED);
    showProgressBlock(false);
    progressBar.setVisible(false);
    stateLabel.setText("ОШИБКА");
  }

  public void cancel() {
    message.setState(Message.State.CANCELLED);
  }

  public void complete() {
    message.setState(Message.State.SENT);
    hideProgressBlock();
  }



  private void showProgressBlock(boolean isProgressBarVisible) {
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
          if (onCancel != null) {
            onCancel.run();
          }
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
    progressBar = null;
    stateLabel = null;
    cancelLabel = null;
    progressBlock = null;
  }

  private void trackCryptoProgress(CryptoProgress<?> progress) {
    ChainableFuture.runStronglyAsync(() -> {
      while (!progress.isDone()) {
        Platform.runLater(() -> setProgress(progress.getProgress()));
        Thread.sleep(200);
        if (message.getState().equals(Message.State.CANCELLED)) {
          progress.cancel(true);
        }
      }
      Platform.runLater(() -> setProgress(progress.getProgress()));
      if (progress.isCompletedExceptionally()) {
        FxUtil.runOnFxThread(this::fail);
      }
    });
  }

  private void trackHttpMultipartProgress(HttpMultipartProgress progress) {
    ChainableFuture.runStronglyAsync(() -> {
      while (!progress.isDone()) {
        Platform.runLater(() -> setProgress(progress.getProgress()));
        Thread.sleep(200);
        if (message.getState().equals(Message.State.CANCELLED)) {
          progress.cancel(true);
        }
      }
      Platform.runLater(() -> setProgress(progress.getProgress()));
      if (progress.isCompletedExceptionally()) {
        FxUtil.runOnFxThread(this::fail);
      }
    });
  }

  private void setProgress(double value) {
    if (progressBar != null) {
      progressBar.setProgress(value);
    }
  }

  private void addPseudoClassListener(BooleanProperty property, String pseudoClassName) {
    property.addListener(_ -> pseudoClassStateChanged(
        PseudoClass.getPseudoClass(pseudoClassName), property.get()));
  }
}
