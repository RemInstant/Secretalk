package org.reminstant.concurrent.functions;

@FunctionalInterface
public interface ThrowingConsumer<T> {
  void accept(T t) throws Exception;
}
