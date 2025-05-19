package org.reminstant.cryptomessengerclient.application.control;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import lombok.Getter;
import org.reminstant.cryptomessengerclient.model.Chat;

import java.math.BigInteger;

public class SecretChatEntry extends Label {

  @Getter
  private final Chat chat;

  private final BooleanProperty active;

  @Getter
  private final ObjectProperty<Chat.State> stateProperty;
  private final BooleanProperty pending;
  private final BooleanProperty awaiting;
  private final BooleanProperty connected;
  private final BooleanProperty disconnected;
  private final BooleanProperty rejected;
  private final BooleanProperty deserted;
  private final BooleanProperty destroyed;

  public SecretChatEntry(Chat chat) {
    super();
    this.chat = chat;
    this.setText(chat.getTitle());
    getStyleClass().clear();
    getStyleClass().add("secretChatEntry");

    stateProperty = new SimpleObjectProperty<>(chat.getState());
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

    getStateProperty(chat.getState()).setValue(true);
  }

  public void setChatActive(boolean isChatActive) {
    active.setValue(isChatActive);
  }

  public void setPendingState(BigInteger privateKey) {
    getStateProperty(chat.getState()).setValue(false);
    getStateProperty(Chat.State.PENDING).setValue(true);
    stateProperty.setValue(Chat.State.PENDING);
    chat.setState(Chat.State.PENDING);
    chat.setKey(privateKey);
  }

  public void setAwaitingState(BigInteger publicKey) {
    getStateProperty(chat.getState()).setValue(false);
    getStateProperty(Chat.State.AWAITING).setValue(true);
    stateProperty.setValue(Chat.State.AWAITING);
    chat.setState(Chat.State.AWAITING);
    chat.setKey(publicKey);
  }

  public void setConnectedState(BigInteger sessionKey) {
    getStateProperty(chat.getState()).setValue(false);
    getStateProperty(Chat.State.CONNECTED).setValue(true);
    stateProperty.setValue(Chat.State.CONNECTED);
    chat.setState(Chat.State.CONNECTED);
    chat.setKey(sessionKey);
  }

  public void setDisconnectedState() {
    getStateProperty(chat.getState()).setValue(false);
    getStateProperty(Chat.State.DISCONNECTED).setValue(true);
    stateProperty.setValue(Chat.State.DISCONNECTED);
    chat.setState(Chat.State.DISCONNECTED);
    chat.setKey(null);
  }

  public void setDesertedState() {
    getStateProperty(chat.getState()).setValue(false);
    getStateProperty(Chat.State.DESERTED).setValue(true);
    stateProperty.setValue(Chat.State.DESERTED);
    chat.setState(Chat.State.DESERTED);
    chat.setKey(null);
  }

  public void setDestroyedState() {
    getStateProperty(chat.getState()).setValue(false);
    getStateProperty(Chat.State.DESTROYED).setValue(true);
    stateProperty.setValue(Chat.State.DESTROYED);
    chat.setState(Chat.State.DESTROYED);
    chat.setKey(null);
  }

  private BooleanProperty getStateProperty(Chat.State state) {
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
