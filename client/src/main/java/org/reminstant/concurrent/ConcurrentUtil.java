package org.reminstant.concurrent;

public class ConcurrentUtil {

  private ConcurrentUtil() {}

  public static void sleepSafely(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }
}