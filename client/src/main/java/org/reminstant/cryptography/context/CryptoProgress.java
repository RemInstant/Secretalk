package org.reminstant.cryptography.context;

import org.reminstant.concurrent.ChainableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class CryptoProgress<T> {

  private final AtomicLong processedBlocksCount;

  private long blockCount;
  private ChainableFuture<T> future;

  CryptoProgress() {
    this.processedBlocksCount = new AtomicLong(0);
    this.blockCount = 1;
  }

  public ChainableFuture<T> getFuture() {
    return future;
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

  public T getResult() throws ExecutionException, InterruptedException {
    return future.get();
  }

  public T getResult(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
    return future.get(timeout, unit);
  }

  public double getProgress() {
    if (future == null) {
      return 1;
    }
    return 1.0 * processedBlocksCount.get() / blockCount;
  }

  void setBlockCount(long blockCount) {
    this.blockCount = blockCount;
  }

  void setFuture(ChainableFuture<T> future) {
    this.future = future;
  }

  long getBlockCount() {
    return blockCount;
  }

  void incrementProcessedBlocksCount() {
    if (future != null) {
      processedBlocksCount.incrementAndGet();
    }
  }
}
