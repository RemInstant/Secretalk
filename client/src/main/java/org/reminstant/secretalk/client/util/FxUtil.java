package org.reminstant.secretalk.client.util;

import javafx.application.Platform;

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
}
