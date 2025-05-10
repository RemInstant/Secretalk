package org.reminstant.concurrent.functions;

@FunctionalInterface
public interface ThrowingRunnable {
  void run() throws Exception;
}
