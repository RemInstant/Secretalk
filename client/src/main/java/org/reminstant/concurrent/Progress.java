package org.reminstant.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface Progress<T> {

  boolean isDone();

  boolean isCancelled();

  boolean isCompletedExceptionally();

  boolean cancel(boolean mayInterruptIfRunning);

  T getResult() throws ExecutionException, InterruptedException;

  T getResult(long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException;

  double getProgress();


  interface Counter {

    double getProgress();

    void setSubTaskCount(long subTaskCount);

    void setCompletedSubTaskCount(long completedSubTaskCount);

    void incrementProgress();

  }
}
