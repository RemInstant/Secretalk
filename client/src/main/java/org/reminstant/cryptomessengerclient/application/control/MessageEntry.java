package org.reminstant.cryptomessengerclient.application.control;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.cryptography.context.CryptoProgress;
import org.reminstant.cryptomessengerclient.model.Message;

import java.util.Objects;

public class MessageEntry extends HBox {

  @Getter
  private final Message message;

  private ProgressBar progressBar;
  private Label stateLabel;
  private Label cancelLabel;

  public MessageEntry(Message message) {
    super();
    this.message = message;
    getStyleClass().setAll("messageEntry");

    this.progressBar = new ProgressBar(0);
    this.stateLabel = new Label("подготовка");
    this.cancelLabel = new Label("x");

    progressBar.getStyleClass().add("messageProgressBar");
    stateLabel.getStyleClass().add("stateLabel");
    cancelLabel.getStyleClass().add("messageCancelLabel");

    Label messageText = new Label(message.getText());
    Pane filler = new Pane();

    VBox messageBlock;
    if (message.isBelongedToReceiver()) {
      HBox progressBlock = new HBox(cancelLabel, progressBar, stateLabel);
      progressBlock.getStyleClass().add("messageProgressBlock");

      messageBlock = new VBox(messageText, progressBlock);
      this.getChildren().addAll(filler, messageBlock);
    } else {
      Label messageAuthor = new Label(message.getAuthor());
      messageAuthor.getStyleClass().add("messageAuthor");

      messageBlock = new VBox(messageText, messageAuthor);
      this.getChildren().addAll(messageBlock, filler);
    }

    messageText.getStyleClass().add("messageText");
    messageBlock.getStyleClass().add("messageBlock");

    HBox.setHgrow(progressBar, Priority.ALWAYS);
    HBox.setHgrow(filler, Priority.ALWAYS);
  }

  public void startEncrypting(CryptoProgress<?> progress) {
    stateLabel.setText("шифрование");
    trackProgress(progress);
  }

  private void trackProgress(CryptoProgress<?> progress) {
    Objects.requireNonNull(progress, "progress cannot be null");
    ChainableFuture.runStronglyAsync(() -> {
      while (!progress.isDone()) {
        Platform.runLater(() -> progressBar.setProgress(progress.getProgress()));
        Thread.sleep(200);
      }
      Platform.runLater(() -> progressBar.setProgress(progress.getProgress()));
    });
  }

  private void addPseudoClassListener(BooleanProperty property, String pseudoClassName) {
    property.addListener(_ -> pseudoClassStateChanged(
        PseudoClass.getPseudoClass(pseudoClassName), property.get()));
  }
}
