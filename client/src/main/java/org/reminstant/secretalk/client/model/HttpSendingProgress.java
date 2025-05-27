package org.reminstant.secretalk.client.model;

import lombok.Getter;
import lombok.Setter;
import org.reminstant.concurrent.ChainableFuture;
import org.reminstant.concurrent.Progress;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class HttpSendingProgress implements Progress<Integer> {

  private final HttpSendingProgress.Counter counter;

  public HttpSendingProgress(HttpSendingProgress.Counter counter) {
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
  public Integer getResult() throws ExecutionException, InterruptedException {
    return counter.getFuture().get();
  }

  @Override
  public Integer getResult(long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    return counter.getFuture().get(timeout, unit);
  }

  @Override
  public double getProgress() {
    return counter.getProgress();
  }

  public static class Counter implements Progress.Counter {

    private final AtomicLong completedSubTaskCount;
    private final AtomicLong subTaskCount;

    @Getter
    @Setter
    private ChainableFuture<Integer> future;

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
  }
}
