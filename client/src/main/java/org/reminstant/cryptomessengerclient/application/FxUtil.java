package org.reminstant.cryptomessengerclient.application;

import javafx.application.Platform;

import java.util.function.Supplier;

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
