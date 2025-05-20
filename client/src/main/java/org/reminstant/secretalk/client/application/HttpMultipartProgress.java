package org.reminstant.secretalk.client.application;

import lombok.Getter;
import org.reminstant.concurrent.ChainableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class HttpMultipartProgress {

  private final AtomicLong processedHttpRequests;

  private long httpRequestsCount;
  @Getter
  private ChainableFuture<Integer> future;

  HttpMultipartProgress() {
    this.processedHttpRequests = new AtomicLong(0);
    this.httpRequestsCount = 1;
  }

  public boolean cancel(boolean mayInterruptIfRunning) {
    return future.cancel(mayInterruptIfRunning);
  }

  public boolean isDone() {
    return future.isDone();
  }

  public boolean isCancelled() {
    return future.isCancelled();
  }

  public boolean isCompletedExceptionally() {
    return future.isCompletedExceptionally();
  }

  public int getResult() throws ExecutionException, InterruptedException {
    return future.get();
  }

  public int getResult(long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    return future.get(timeout, unit);
  }

  public double getProgress() {
    if (future == null) {
      return 1;
    }
    return 1.0 * processedHttpRequests.get() / httpRequestsCount;
  }

  void setRequestsCount(long blockCount) {
    this.httpRequestsCount = blockCount;
  }

  void setFuture(ChainableFuture<Integer> future) {
    this.future = future;
  }

  void incrementProcessedBlocksCount() {
    if (future != null) {
      processedHttpRequests.incrementAndGet();
    }
  }
}
