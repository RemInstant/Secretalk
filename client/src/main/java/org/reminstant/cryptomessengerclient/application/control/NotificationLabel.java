package org.reminstant.cryptomessengerclient.application.control;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
public class NotificationLabel extends Label {

  private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private Future<?> glowRemoveFuture = null;

  private final BooleanProperty collapsed;
  private final BooleanProperty isSuccess;
  private final BooleanProperty isError;
  private final BooleanProperty glowing;

  public NotificationLabel() {
    super();
    getStyleClass().clear();
    getStyleClass().add("notificationLabel");

    collapsed = new SimpleBooleanProperty(true);
    glowing = new SimpleBooleanProperty(false);
    isSuccess = new SimpleBooleanProperty(false);
    isError = new SimpleBooleanProperty(false);

    addPseudoClassListener(collapsed, "collapsed");
    addPseudoClassListener(isSuccess, "isSuccess");
    addPseudoClassListener(isError, "isError");
    addPseudoClassListener(glowing, "glowing");

    pseudoClassStateChanged(PseudoClass.getPseudoClass("collapsed"), true);
  }


  public void showSuccess(String text) {
    setSuccess();
    updateGlowing();
    setText(text);
  }

  public void showError(String text) {
    setError();
    updateGlowing();
    setText(text);
  }

  public void collapse() {
    collapsed.setValue(true);
    isSuccess.setValue(false);
    isError.setValue(false);
  }

  public void setSuccess() {
    collapsed.setValue(false);
    isSuccess.setValue(true);
    isError.setValue(false);
  }

  public void setError() {
    collapsed.setValue(false);
    isSuccess.setValue(false);
    isError.setValue(true);
  }

  public void updateGlowing() {
    glowing.setValue(true);
    if (glowRemoveFuture != null) {
      glowRemoveFuture.cancel(true);
    }
    glowRemoveFuture = executor.submit(() -> {
      try {
        Thread.sleep(2000);
        glowing.setValue(false);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    });
  }

  private void addPseudoClassListener(BooleanProperty property, String pseudoClassName) {
    property.addListener(_ -> pseudoClassStateChanged(
        PseudoClass.getPseudoClass(pseudoClassName), property.get()));
  }
}
