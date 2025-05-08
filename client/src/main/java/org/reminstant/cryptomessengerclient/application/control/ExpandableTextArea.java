package org.reminstant.cryptomessengerclient.application.control;

import javafx.scene.control.TextArea;
import javafx.scene.text.Text;

public class ExpandableTextArea extends TextArea {

//  private final Text textHolder = new Text();

  public ExpandableTextArea() {
    super();

    Text textHolder = new Text();
    TextArea messageInput = this;

    textHolder.wrappingWidthProperty().bind(messageInput.widthProperty().subtract(40));
    textHolder.fontProperty().bind(messageInput.fontProperty());

    textHolder.textProperty().bind(messageInput.textProperty());
    textHolder.layoutBoundsProperty().addListener((_, _, newValue) -> {
      double calculatedHeight = Math.round(newValue.getHeight() / 20) * 25.0 + 10.0;
      messageInput.setMinHeight(Double.min(110, calculatedHeight));
    });
  }
}
