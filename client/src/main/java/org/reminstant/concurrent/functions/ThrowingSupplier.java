package org.reminstant.concurrent.functions;

@FunctionalInterface
public interface ThrowingSupplier<T> {
  T get() throws Exception;
}
