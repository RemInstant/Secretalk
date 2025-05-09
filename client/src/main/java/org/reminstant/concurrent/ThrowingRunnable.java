package org.reminstant.concurrent;

import java.util.concurrent.Callable;

@FunctionalInterface
public interface ThrowingRunnable {
  void run() throws Exception;

  static Callable<Void> toCallable(ThrowingRunnable runnable) {
    return () -> {
      runnable.run();
      return null;
    };
  }

  static <T> Callable<T> toCallable(ThrowingRunnable runnable, T value) {
    return () -> {
      runnable.run();
      return value;
    };
  }
}
