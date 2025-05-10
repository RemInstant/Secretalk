package org.reminstant.concurrent.functions;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ThrowingFunctions {

  private ThrowingFunctions() {

  }

  public static ThrowingSupplier<Void> toSupplier(ThrowingRunnable runnable) {
    return () -> {
      runnable.run();
      return null;
    };
  }

  public static ThrowingSupplier<Void> toSupplier(Runnable runnable) {
    return toSupplier(convert(runnable));
  }

  public static ThrowingRunnable convert(Runnable runnable) {
    return runnable::run;
  }

  public static <T, R> ThrowingFunction<T, R> convert(Function<T, R> function) {
    return function::apply;
  }

  public static <T> ThrowingConsumer<T> convert(Consumer<T> consumer) {
    return consumer::accept;
  }

  public static <T> ThrowingSupplier<T> convert(Supplier<T> supplier) {
    return supplier::get;
  }

}
