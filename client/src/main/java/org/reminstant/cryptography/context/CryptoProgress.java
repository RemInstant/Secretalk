package org.reminstant.cryptography.context;

import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.concurrent.Progress;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class CryptoProgress<T> implements Progress<T> {

  private final CryptoProgress.Counter<T> counter;

  CryptoProgress(CryptoProgress.Counter<T> counter) {
    this.counter = counter;
  }

  @Override
  public boolean isDone() {
    return counter.getFuture().isDone();
  }

  @Override
  public boolean isCancelled() {
    return counter.getFuture().isCancelled();
  }

  @Override
  public boolean isCompletedExceptionally() {
    return counter.getFuture().isCompletedExceptionally();
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return counter.getFuture().cancel(mayInterruptIfRunning);
  }

  @Override
  public T getResult() throws ExecutionException, InterruptedException {
    return counter.getFuture().get();
  }

  @Override
  public T getResult(long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    return counter.getFuture().get(timeout, unit);
  }

  @Override
  public double getProgress() {
    return counter.getProgress();
  }

  public ChainableFuture<T> getFuture() {
    return counter.getFuture();
  }


  public static class Counter<R> implements Progress.Counter {

    private final AtomicLong completedSubTaskCount;
    private final AtomicLong subTaskCount;

    private ChainableFuture<R> future;

    public Counter() {
      this.completedSubTaskCount = new AtomicLong(0);
      this.subTaskCount = new AtomicLong(1);
    }

    @Override
    public double getProgress() {
      if (future == null) {
        return 0;
      }
      return 1.0 * completedSubTaskCount.get() / subTaskCount.get();
    }

    @Override
    public void setSubTaskCount(long subTaskCount) {
      this.subTaskCount.set(subTaskCount);
    }

    @Override
    public void setCompletedSubTaskCount(long completedSubTaskCount) {
      this.completedSubTaskCount.set(completedSubTaskCount);
    }

    @Override
    public void incrementProgress() {
      completedSubTaskCount.incrementAndGet();
    }
    
    public ChainableFuture<R> getFuture() {
      return future;
    }

    public void setFuture(ChainableFuture<R> future) {
      this.future = future;
    }
  }
}
