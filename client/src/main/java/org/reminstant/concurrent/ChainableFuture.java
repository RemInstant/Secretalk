package org.reminstant.concurrent;

import org.reminstant.concurrent.functions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class ChainableFuture<V> implements Future<V> {

  private static final ExecutorService DEFAULT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
  private static final ChainableFuture<Void> COMPLETED_VOID_INSTANCE =
      new ChainableFuture<>(DEFAULT_EXECUTOR, () -> null, true);

  private final ExecutorService executor;

  private final Future<V> currentTask;
  private final boolean isStrong;
  private final Set<ChainableFuture<?>> parentTasks;
  private final AtomicInteger childrenCount;



  public static ChainableFuture<Void> getCompleted() {
    return COMPLETED_VOID_INSTANCE;
  }


  public static ChainableFuture<Void> runStronglyAsync(ThrowingRunnable runnable) {
    return supplyStronglyAsync(ThrowingFunctions.toSupplier(runnable));
  }

  public static ChainableFuture<Void> runWeaklyAsync(ThrowingRunnable runnable) {
    return supplyWeaklyAsync(ThrowingFunctions.toSupplier(runnable));
  }

  public static ChainableFuture<Void> runWeaklyAsync(ThrowingRunnable runnable, ExecutorService executor) {
    return supplyStronglyAsync(ThrowingFunctions.toSupplier(runnable), executor);
  }

  public static ChainableFuture<Void> runStronglyAsync(ThrowingRunnable runnable, ExecutorService executor) {
    return supplyWeaklyAsync(ThrowingFunctions.toSupplier(runnable), executor);
  }


  public static <V> ChainableFuture<V> supplyStronglyAsync(ThrowingSupplier<V> supplier) {
    return new ChainableFuture<>(DEFAULT_EXECUTOR, supplier, true);
  }

  public static <V> ChainableFuture<V> supplyWeaklyAsync(ThrowingSupplier<V> supplier) {
    return new ChainableFuture<>(DEFAULT_EXECUTOR, supplier, false);
  }

  public static <V> ChainableFuture<V> supplyStronglyAsync(ThrowingSupplier<V> supplier, ExecutorService executor) {
    return new ChainableFuture<>(executor, supplier, true);
  }

  public static <V> ChainableFuture<V> supplyWeaklyAsync(ThrowingSupplier<V> supplier, ExecutorService executor) {
    return new ChainableFuture<>(executor, supplier, false);
  }


  public static <V> ChainableFuture<Void> awaitAllStronglyAsync(Iterable<ChainableFuture<V>> futures) {
    return awaitAllAsync(futures, true, DEFAULT_EXECUTOR);
  }

  public static <V> ChainableFuture<Void> awaitAllWeaklyAsync(Iterable<ChainableFuture<V>> futures) {
    return awaitAllAsync(futures, false, DEFAULT_EXECUTOR);
  }

  public static <V> ChainableFuture<Void> awaitAllStronglyAsync(Iterable<ChainableFuture<V>> futures,
                                                                ExecutorService executor) {
    return awaitAllAsync(futures, true, executor);
  }

  public static <V> ChainableFuture<Void> awaitAllWeaklyAsync(Iterable<ChainableFuture<V>> futures,
                                                              ExecutorService executor) {
    return awaitAllAsync(futures, false, executor);
  }



  private ChainableFuture(ExecutorService executor, ThrowingSupplier<V> supplier, boolean isStrong) {
    this.executor = executor;
    this.currentTask = executor.submit(supplier::get);
    this.isStrong = isStrong;
    this.parentTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    this.childrenCount = new AtomicInteger(0);
  }



  public <U> ChainableFuture<U> thenStronglyMapAsync(ThrowingFunction<? super V, U> mapping) {
    return thenMapAsync(mapping, true, executor);
  }

  public <U> ChainableFuture<U> thenWeaklyMapAsync(ThrowingFunction<? super V, U> mapping) {
    return thenMapAsync(mapping, false, executor);
  }

  public ChainableFuture<Void> thenStronglyRunAsync(ThrowingRunnable runnable) {
    ThrowingFunction<V, Void> mapping = _ -> {
      runnable.run();
      return null;
    };
    return thenMapAsync(mapping, true, executor);
  }

  public ChainableFuture<Void> thenWeaklyRunAsync(ThrowingRunnable runnable) {
    ThrowingFunction<V, Void> mapping = _ -> {
      runnable.run();
      return null;
    };
    return thenMapAsync(mapping, false, executor);
  }

  public ChainableFuture<Void> thenStronglyConsumeAsync(ThrowingConsumer<V> consumer) {
    ThrowingFunction<V, Void> mapping = value -> {
      consumer.accept(value);
      return null;
    };
    return thenMapAsync(mapping, true, executor);
  }

  public ChainableFuture<Void> thenWeaklyConsumeAsync(ThrowingConsumer<V> consumer) {
    ThrowingFunction<V, Void> mapping = value -> {
      consumer.accept(value);
      return null;
    };
    return thenMapAsync(mapping, false, executor);
  }

  public <U> ChainableFuture<U> thenStronglySupplyAsync(ThrowingSupplier<U> supplier) {
    ThrowingFunction<V, U> mapping = _ -> supplier.get();
    return thenMapAsync(mapping, true, executor);
  }

  public <U> ChainableFuture<U> thenWeaklySupplyAsync(ThrowingSupplier<U> supplier) {
    ThrowingFunction<V, U> mapping = _ -> supplier.get();
    return thenMapAsync(mapping, false, executor);
  }

  public ChainableFuture<V> thenStronglyHandleAsync(ThrowingFunction<? super Exception, V> handler) {
    return thenHandleAsync(handler, true, executor);
  }

  public ChainableFuture<V> thenWeaklyHandleAsync(ThrowingFunction<? super Exception, V> handler) {
    return thenHandleAsync(handler, false, executor);
  }

  public <U> ChainableFuture<U> thenStronglyComposeAsync(Function<? super V, ? extends Future<U>> function) {
    return thenComposeAsync(function, true, executor);
  }

  public <U> ChainableFuture<U> thenWeaklyComposeAsync(Function<? super V, ? extends Future<U>> function) {
    return thenComposeAsync(function, false, executor);
  }



  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean res = currentTask.cancel(mayInterruptIfRunning);
    sendCancellationNotifications(mayInterruptIfRunning);
    return res;
  }

//  @Override
//  public boolean cancelRecursively(boolean mayInterruptIfRunning) {
//    boolean res = currentTask.cancel(mayInterruptIfRunning);
//    sendCancellationNotifications(mayInterruptIfRunning);
//    return res;
//  }

  @Override
  public boolean isCancelled() {
    return currentTask.isCancelled();
  }

  @Override
  public boolean isDone() {
    return currentTask.isDone();
  }

  @Override
  public V get() throws ExecutionException, InterruptedException {
    try {
      return currentTask.get();
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof ChainExecutionException chainEx) {
        throw new ExecutionException(ChainExecutionException.PARENT_FAILURE, chainEx);
      }
      throw ex;
    } catch (CancellationException ex) {
      throw new ExecutionException(ChainExecutionException.CANCELLATION, ex);
    }
  }

  @Override
  public V get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
    try {
      return currentTask.get(timeout, unit);
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof ChainExecutionException chainEx) {
        throw new ExecutionException(ChainExecutionException.PARENT_FAILURE, chainEx);
      }
      throw ex;
    } catch (CancellationException ex) {
      throw new ExecutionException(ex);
    }
  }

  public boolean isCompletedExceptionally() {
    try {
      //noinspection ThrowableNotThrown
      exceptionNow();
      return true;
    } catch (IllegalStateException ex) {
      return false;
    }
  }

  public void waitCompletion() throws InterruptedException {
    try {
      get();
    } catch (ExecutionException _) { } // NOSONAR
  }

  public void waitCompletion(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    try {
      get(timeout, unit);
    }  catch (ExecutionException _) { } // NOSONAR
  }

  public boolean isStrong() {
    return isStrong;
  }



  private static <V> ChainableFuture<Void> awaitAllAsync(Iterable<ChainableFuture<V>> futures,
                                                         boolean isStrong, ExecutorService executor) {
    ThrowingSupplier<Void> supplier = () -> {
      for (ChainableFuture<V> future : futures) {
        try {
          future.get();
        } catch (InterruptedException _) {
          throw new InterruptedException();
        } catch (Exception _) { } // NOSONAR
      }
      return null;
    };

    ChainableFuture<Void> childTask = new ChainableFuture<>(executor, supplier, isStrong);
    for (ChainableFuture<V> future : futures) {
      childTask.parentTasks.add(future);
      future.childrenCount.incrementAndGet();
    }

    return childTask;
  }

  private static <V> ChainableFuture<List<V>> collectAsync(Iterable<ChainableFuture<V>> futures,
                                                           boolean isStrong, ExecutorService executor,
                                                           boolean mayInterruptIfRunning) {
    ThrowingSupplier<List<V>> supplier = () -> {
      try {
        List<V> result = new ArrayList<>();
        for (ChainableFuture<V> future : futures) {
          result.add(future.get());
        }
        return result;
      } catch (ExecutionException ex) {
        cancelParents(futures, mayInterruptIfRunning);
        throw new ChainExecutionException(ex);
      } catch (CancellationException ex) { // NOSONAR
        cancelParents(futures, mayInterruptIfRunning);
        throw new ChainExecutionException(ex);
      }
    };

    ChainableFuture<List<V>> childTask = new ChainableFuture<>(executor, supplier, isStrong);
    for (ChainableFuture<V> future : futures) {
      childTask.parentTasks.add(future);
      future.childrenCount.incrementAndGet();
    }

    return childTask;
  }

  private static <V> void cancelParents(Iterable<ChainableFuture<V>> parents, boolean mayInterruptIfRunning) {
    for (ChainableFuture<V> parent : parents) {
      parent.handleCancellationNotification(mayInterruptIfRunning);
    }
  }



  private <U> ChainableFuture<U> thenMapAsync(ThrowingFunction<? super V, U> mapping,
                                              boolean isStrong, ExecutorService executor) {
    ThrowingSupplier<U> supplier = () -> {
      try {
        return mapping.apply(currentTask.get());
      } catch (ExecutionException ex) {
        // It's currentTask exception
        // Mapping exception will be thrown when childTask will be gotten
        throw new ChainExecutionException(ex);
      } catch (CancellationException ex) { // NOSONAR
        throw new ChainExecutionException(ex);
      }
    };

    ChainableFuture<U> childTask = new ChainableFuture<>(executor, supplier, isStrong);
    childTask.parentTasks.add(this);
    childrenCount.incrementAndGet();

    return childTask;
  }

  private ChainableFuture<V> thenHandleAsync(ThrowingFunction<? super Exception, V> handler,
                                             boolean isStrong, ExecutorService executor) {
    ThrowingSupplier<V> supplier = () -> {
      try {
        return currentTask.get();
      } catch (InterruptedException ex) {
        throw new InterruptedException();
      } catch (ExecutionException ex) {
        if (ex.getCause() instanceof Exception cause) {
          return handler.apply(cause);
        }
        throw new ChainExecutionException(ex);
      } catch (Exception ex) {
        return handler.apply(ex);
      }
    };

    ChainableFuture<V> childTask = new ChainableFuture<>(executor, supplier, isStrong);
    childTask.parentTasks.add(this);
    childrenCount.incrementAndGet();

    return childTask;
  }

  private <U> ChainableFuture<U> thenComposeAsync(Function<? super V, ? extends Future<U>> function,
                                                  boolean isStrong, ExecutorService executor) {
    ThrowingSupplier<U> supplier = () -> {
      try {
        return function.apply(currentTask.get()).get();
      } catch (ExecutionException ex) {
        throw new ChainExecutionException(ex);
      } catch (CancellationException ex) { // NOSONAR
        throw new ChainExecutionException(ex);
      }
    };

    ChainableFuture<U> childTask = new ChainableFuture<>(executor, supplier, isStrong);
    childTask.parentTasks.add(this);
    childrenCount.incrementAndGet();

    return childTask;
  }



  private void sendCancellationNotifications(boolean mayInterruptIfRunning) {
    for (ChainableFuture<?> parent : parentTasks) {
      parent.handleCancellationNotification(mayInterruptIfRunning);
    }
    parentTasks.clear();
  }

  private void handleCancellationNotification(boolean mayInterruptIfRunning) {
    if (!isStrong && (childrenCount.decrementAndGet() == 0)) {
      cancel(mayInterruptIfRunning);
    }
  }
}
