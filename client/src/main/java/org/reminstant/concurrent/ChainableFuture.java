package org.reminstant.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class ChainableFuture<V> implements Future<V> {

  private static final ExecutorService DEFAULT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private final ExecutorService executor;

  private final Future<V> currentTask;
  private final boolean isStrong;
  private final Set<ChainableFuture<?>> parentTasks;
  private final AtomicInteger childrenCount;
  
  
  
  public static ChainableFuture<Void> runStronglyAsync(Runnable runnable) {
    return executeStronglyAsync(Executors.callable(runnable, null));
  }

  public static ChainableFuture<Void> runWeaklyAsync(Runnable runnable) {
    return executeWeaklyAsync(Executors.callable(runnable, null));
  }
  
  public static ChainableFuture<Void> runStronglyAsync(Runnable runnable, ExecutorService executor) {
    return executeStronglyAsync(Executors.callable(runnable, null), executor);
  }

  public static ChainableFuture<Void> runWeaklyAsync(Runnable runnable, ExecutorService executor) {
    return executeWeaklyAsync(Executors.callable(runnable, null), executor);
  }



  public static ChainableFuture<Void> executeStronglyAsync(ThrowingRunnable runnable) {
    return executeStronglyAsync(ThrowingRunnable.toCallable(runnable));
  }

  public static ChainableFuture<Void> executeWeaklyAsync(ThrowingRunnable runnable) {
    return executeWeaklyAsync(ThrowingRunnable.toCallable(runnable));
  }

  public static ChainableFuture<Void> executeWeaklyAsync(ThrowingRunnable runnable, ExecutorService executor) {
    return executeStronglyAsync(ThrowingRunnable.toCallable(runnable), executor);
  }

  public static ChainableFuture<Void> executeStronglyAsync(ThrowingRunnable runnable, ExecutorService executor) {
    return executeWeaklyAsync(ThrowingRunnable.toCallable(runnable), executor);
  }

  
  
  public static <V> ChainableFuture<V> executeStronglyAsync(Callable<V> callable) {
    return new ChainableFuture<>(DEFAULT_EXECUTOR, callable, true);
  }

  public static <V> ChainableFuture<V> executeWeaklyAsync(Callable<V> callable) {
    return new ChainableFuture<>(DEFAULT_EXECUTOR, callable, false);
  }

  public static <V> ChainableFuture<V> executeStronglyAsync(Callable<V> callable, ExecutorService executor) {
    return new ChainableFuture<>(executor, callable, true);
  }

  public static <V> ChainableFuture<V> executeWeaklyAsync(Callable<V> callable, ExecutorService executor) {
    return new ChainableFuture<>(executor, callable, false);
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



  private ChainableFuture(ExecutorService executor, Callable<V> callable, boolean isStrong) {
    this.executor = executor;
    this.currentTask = executor.submit(callable);
    this.isStrong = isStrong;
    this.parentTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    this.childrenCount = new AtomicInteger(0);
  }

  
  
  public <U> ChainableFuture<U> thenStronglyMapAsync(Function<? super V, U> mapping) {
    return thenMapAsync(mapping, true, executor);
  }

  public <U> ChainableFuture<U> thenWeaklyMapAsync(Function<? super V, U> mapping) {
    return thenMapAsync(mapping, false, executor);
  }

  public ChainableFuture<Void> thenStronglyRunAsync(Runnable runnable) {
    Function<V, Void> mapping = _ -> {
      runnable.run();
      return null;
    };
    return thenMapAsync(mapping, true, executor);
  }

  public ChainableFuture<Void> thenWeaklyRunAsync(Runnable runnable) {
    Function<V, Void> mapping = _ -> {
      runnable.run();
      return null;
    };
    return thenMapAsync(mapping, false, executor);
  }

//  public ChainableFuture<Void> thenStronglyExecuteAsync(ThrowingRunnable runnable) {
//    return thenMapAsync(mapping, true, executor);
//  }
//
//  public ChainableFuture<Void> thenWeaklyExecuteAsync(ThrowingRunnable runnable) {
//    return thenMapAsync(mapping, false, executor);
//  }

  public ChainableFuture<V> thenStronglyHandleAsync(Function<? super Throwable, V> handler) {
    return thenHandleAsync(handler, true, executor);
  }

  public ChainableFuture<V> thenWeaklyHandleAsync(Function<? super Throwable, V> handler) {
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
    Callable<Void> callable = () -> {
      for (ChainableFuture<V> future : futures) {
        try {
          future.get();
        } catch (InterruptedException _) {
          throw new InterruptedException();
        } catch (Exception _) { } // NOSONAR
      }
      return null;
    };

    ChainableFuture<Void> childTask = new ChainableFuture<>(executor, callable, isStrong);
    for (ChainableFuture<V> future : futures) {
      childTask.parentTasks.add(future);
      future.childrenCount.incrementAndGet();
    }

    return childTask;
  }

  private static <V> ChainableFuture<List<V>> collectAsync(Iterable<ChainableFuture<V>> futures,
                                                           boolean isStrong, ExecutorService executor,
                                                           boolean mayInterruptIfRunning) {
    Callable<List<V>> callable = () -> {
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

    ChainableFuture<List<V>> childTask = new ChainableFuture<>(executor, callable, isStrong);
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



  private <U> ChainableFuture<U> thenMapAsync(Function<? super V, U> mapping, boolean isStrong,
                                              ExecutorService executor) {
    Callable<U> callable = () -> {
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

    ChainableFuture<U> childTask = new ChainableFuture<>(executor, callable, isStrong);
    childTask.parentTasks.add(this);
    childrenCount.incrementAndGet();

    return childTask;
  }

  private ChainableFuture<V> thenHandleAsync(Function<? super Throwable, V> handler, boolean isStrong,
                                             ExecutorService executor) {
    Callable<V> callable = () -> {
      try {
        return currentTask.get();
      } catch (InterruptedException ex) {
        throw new InterruptedException();
      } catch (ExecutionException ex) {
        return handler.apply(ex.getCause());
      } catch (Exception ex) {
        return handler.apply(ex);
      }
    };

    ChainableFuture<V> childTask = new ChainableFuture<>(executor, callable, isStrong);
    childTask.parentTasks.add(this);
    childrenCount.incrementAndGet();

    return childTask;
  }

  private <U> ChainableFuture<U> thenComposeAsync(Function<? super V, ? extends Future<U>> function,
                                                  boolean isStrong, ExecutorService executor) {
    Callable<U> callable = () -> {
      try {
        return function.apply(currentTask.get()).get();
      } catch (ExecutionException ex) {
        throw new ChainExecutionException(ex);
      } catch (CancellationException ex) { // NOSONAR
        throw new ChainExecutionException(ex);
      }
    };

    ChainableFuture<U> childTask = new ChainableFuture<>(executor, callable, isStrong);
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
