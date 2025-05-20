package org.reminstant.secretalk.client.application.control;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExpandableTextArea extends TextArea {

  private final Text textHolder = new Text();
  private double rowHeight = 0;

  private int maxLength = 256;

  public ExpandableTextArea() {
    super();
    textHolder.wrappingWidthProperty().bind(this.widthProperty().subtract(40));
    textHolder.fontProperty().bind(this.fontProperty());
    textHolder.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);

    textHolder.textProperty().bind(this.textProperty());
    textHolder.layoutBoundsProperty().addListener(_ -> {
      this.setMinHeight(calculateHeight());
      this.setMaxHeight(calculateHeight());
    });

    this.setTextFormatter(new TextFormatter<>(change -> {
      if (change.getControlNewText().length() > maxLength) {
        int lengthToAdd = maxLength - change.getControlText().length();
        change.setText(change.getText().substring(0, lengthToAdd));
        return change;
      }
      return change;
    }));
  }

  public void setMaxLength(int maxLength) {
    this.maxLength = maxLength;
  }

  private double calculateHeight() {
    if (rowHeight == 0) {
      rowHeight = textHolder.getLayoutBounds().getHeight();
    }
    long rowCount = Math.round(textHolder.getLayoutBounds().getHeight() / rowHeight);
    return Long.min(rowCount, 4) * rowHeight * 1.05 + 10.0;
  }
}
