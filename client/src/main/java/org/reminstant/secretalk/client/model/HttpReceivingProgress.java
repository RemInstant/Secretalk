package org.reminstant.secretalk.client.model;

import lombok.Getter;
import org.reminstant.concurrent.Progress;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class HttpReceivingProgress implements Progress<Void> {

  private final HttpReceivingProgress.Counter counter;
  private final Lock lock;
  private final Condition condition;

  public HttpReceivingProgress(HttpReceivingProgress.Counter counter) {
    this.counter = counter;
    this.lock = new ReentrantLock();
    this.condition = lock.newCondition();
    counter.setupLock(lock, condition);
  }

  @Override
  public boolean isDone() {
    return (counter.getCompletedSubTaskCount() == counter.getSubTaskCount()) || counter.isCancelled();
  }

  @Override
  public boolean isCancelled() {
    return counter.isCancelled();
  }

  @Override
  public boolean isCompletedExceptionally() {
    return false;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return counter.cancel();
  }

  @Override
  public Void getResult() throws ExecutionException, InterruptedException {
    lock.lock();
    try {
      while (!isDone()) {
        condition.await();
      }
      if (isCancelled()) {
        throw new ExecutionException("Task was cancelled", new CancellationException());
      }
      return null;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Void getResult(long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    lock.lock();
    try {
      while (!isDone()) {
        if (!condition.await(timeout, unit)) {
          throw new TimeoutException();
        }
      }
      if (isCancelled()) {
        throw new ExecutionException("Task was cancelled", new CancellationException());
      }
      return null;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public double getProgress() {
    return counter.getProgress();
  }

  public static class Counter implements Progress.Counter {

    private final AtomicLong completedSubTaskCount;
    private final AtomicLong subTaskCount;

    @Getter
    private boolean isCancelled;

    private Lock lock;
    private Condition condition;

    public Counter() {
      this.completedSubTaskCount = new AtomicLong(0);
      this.subTaskCount = new AtomicLong(1);
      this.lock = new ReentrantLock();
      this.condition = lock.newCondition();
    }

    @Override
    public double getProgress() {
      return 1.0 * completedSubTaskCount.get() / subTaskCount.get();
    }

    @Override
    public void setSubTaskCount(long subTaskCount) {
      this.subTaskCount.set(subTaskCount);
    }

    @Override
    public void setCompletedSubTaskCount(long completedSubTaskCount) {
      lock.lock();
      this.completedSubTaskCount.set(completedSubTaskCount);
      condition.signalAll();
      lock.unlock();
    }

    @Override
    public void incrementProgress() {
      lock.lock();
      completedSubTaskCount.incrementAndGet();
      condition.signalAll();
      lock.unlock();
    }

    public long getSubTaskCount() {
      return subTaskCount.get();
    }

    public long getCompletedSubTaskCount() {
      return completedSubTaskCount.get();
    }

    public boolean cancel() {
      isCancelled = true;
      return true;
    }

    private void setupLock(Lock lock, Condition condition) {
      Lock prevLock = this.lock;
      prevLock.lock();
      this.lock = lock;
      this.condition = condition;
      prevLock.unlock();
    }
  }
}
