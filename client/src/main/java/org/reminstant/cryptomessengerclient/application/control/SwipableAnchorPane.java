package org.reminstant.cryptomessengerclient.application.control;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;

public class SwipableAnchorPane extends AnchorPane {

  private final BooleanProperty swiped;

  public SwipableAnchorPane() {
    super();
    swiped = new SimpleBooleanProperty(false);
    swiped.addListener(_ -> pseudoClassStateChanged(
        PseudoClass.getPseudoClass("swiped"), swiped.get()));
  }

  public SwipableAnchorPane(Node... children) {
    this();
    getChildren().addAll(children);
  }

  public void setSwiped(boolean isSwiped) {
    swiped.setValue(isSwiped);
  }
}
