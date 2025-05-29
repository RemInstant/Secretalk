package org.reminstant.secretalk.client.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import org.reminstant.secretalk.client.exception.FxControlNotFoundException;

public class FxUtil {

  private FxUtil() {

  }

  public static void runOnFxThread(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }

  public static <T> T getChildById(Parent parent, String id, Class<T> nodeClass) {
    return parent.getChildrenUnmodifiable().stream()
        .filter(node -> node.getId().equals(id))
        .filter(nodeClass::isInstance)
        .map(nodeClass::cast)
        .findFirst()
        .orElseThrow(() -> new FxControlNotFoundException("%s not found".formatted(id)));
  }
}
