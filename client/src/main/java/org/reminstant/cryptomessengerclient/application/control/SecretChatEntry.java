package org.reminstant.cryptomessengerclient.application.control;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import lombok.Getter;
import org.reminstant.cryptomessengerclient.model.SecretChat;

import java.math.BigInteger;

public class SecretChatEntry extends Label {

  @Getter
  private final SecretChat secretChat;

  private final BooleanProperty active;

  @Getter
  private final ObjectProperty<SecretChat.State> stateProperty;
  private final BooleanProperty pending;
  private final BooleanProperty awaiting;
  private final BooleanProperty connected;
  private final BooleanProperty disconnected;
  private final BooleanProperty rejected;
  private final BooleanProperty deserted;
  private final BooleanProperty destroyed;

  public SecretChatEntry(SecretChat secretChat) {
    super();
    this.secretChat = secretChat;
    this.setText(secretChat.getTitle());
    getStyleClass().clear();
    getStyleClass().add("secretChatEntry");

    stateProperty = new SimpleObjectProperty<>(secretChat.getState());
    active = new SimpleBooleanProperty(false);
    pending = new SimpleBooleanProperty(false);
    awaiting = new SimpleBooleanProperty(false);
    connected = new SimpleBooleanProperty(false);
    disconnected = new SimpleBooleanProperty(false);
    rejected = new SimpleBooleanProperty(false);
    deserted = new SimpleBooleanProperty(false);
    destroyed = new SimpleBooleanProperty(false);

    addPseudoClassListener(active, "active");
    addPseudoClassListener(pending, "pending");
    addPseudoClassListener(awaiting, "awaiting");
    addPseudoClassListener(connected, "connected");
    addPseudoClassListener(disconnected, "disconnected");
    addPseudoClassListener(rejected, "rejected");
    addPseudoClassListener(deserted, "deserted");
    addPseudoClassListener(destroyed, "destroyed");

    getStateProperty(secretChat.getState()).setValue(true);
  }

  public void setChatActive(boolean isChatActive) {
    active.setValue(isChatActive);
  }

  public void setPendingState(BigInteger privateKey) {
    getStateProperty(secretChat.getState()).setValue(false);
    getStateProperty(SecretChat.State.PENDING).setValue(true);
    stateProperty.setValue(SecretChat.State.PENDING);
    secretChat.setState(SecretChat.State.PENDING);
    secretChat.setKey(privateKey);
  }

  public void setAwaitingState(BigInteger publicKey) {
    getStateProperty(secretChat.getState()).setValue(false);
    getStateProperty(SecretChat.State.AWAITING).setValue(true);
    stateProperty.setValue(SecretChat.State.AWAITING);
    secretChat.setState(SecretChat.State.AWAITING);
    secretChat.setKey(publicKey);
  }

  public void setConnectedState(BigInteger sessionKey) {
    getStateProperty(secretChat.getState()).setValue(false);
    getStateProperty(SecretChat.State.CONNECTED).setValue(true);
    stateProperty.setValue(SecretChat.State.CONNECTED);
    secretChat.setState(SecretChat.State.CONNECTED);
    secretChat.setKey(sessionKey);
  }

  public void setDisconnectedState() {
    getStateProperty(secretChat.getState()).setValue(false);
    getStateProperty(SecretChat.State.DISCONNECTED).setValue(true);
    stateProperty.setValue(SecretChat.State.DISCONNECTED);
    secretChat.setState(SecretChat.State.DISCONNECTED);
    secretChat.setKey(null);
  }

  public void setDesertedState() {
    getStateProperty(secretChat.getState()).setValue(false);
    getStateProperty(SecretChat.State.DESERTED).setValue(true);
    stateProperty.setValue(SecretChat.State.DESERTED);
    secretChat.setState(SecretChat.State.DESERTED);
    secretChat.setKey(null);
  }

  public void setDestroyedState() {
    getStateProperty(secretChat.getState()).setValue(false);
    getStateProperty(SecretChat.State.DESTROYED).setValue(true);
    stateProperty.setValue(SecretChat.State.DESTROYED);
    secretChat.setState(SecretChat.State.DESTROYED);
    secretChat.setKey(null);
  }

  private BooleanProperty getStateProperty(SecretChat.State state) {
    return switch (state) {
      case PENDING -> pending;
      case AWAITING -> awaiting;
      case CONNECTED -> connected;
      case DISCONNECTED -> disconnected;
      case DESERTED -> deserted;
      case DESTROYED -> destroyed;
    };
  }

  private void addPseudoClassListener(BooleanProperty property, String pseudoClassName) {
    property.addListener(_ -> pseudoClassStateChanged(
        PseudoClass.getPseudoClass(pseudoClassName), property.get()));
  }
}
