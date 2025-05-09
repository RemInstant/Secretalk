package org.reminstant.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public final class ChainExecutionException extends Exception {

  public static final String FAILURE_TEMPLATE = "Failed due to %s";
  public static final String UNKNOWN_FAILURE = "Failed due to unknown reason";
  public static final String PARENT_FAILURE = "Failed due to parent failure";
  public static final String CANCELLATION = "Failed due to cancellation";
  public static final String INTERRUPTION = "Failed due to foreign interruption";

  public ChainExecutionException(ExecutionException ex) {
    super(
        switch (ex.getCause()) {
          case ChainExecutionException _ -> PARENT_FAILURE;
          case CancellationException _ -> CANCELLATION;
          case null -> UNKNOWN_FAILURE;
          default -> FAILURE_TEMPLATE.formatted(ex.getCause().toString());
        },
        ex.getCause());
  }

  public ChainExecutionException(InterruptedException ex) {
    super(INTERRUPTION, ex);
  }

  public ChainExecutionException(CancellationException ex) {
    super(CANCELLATION, ex);
  }
}
