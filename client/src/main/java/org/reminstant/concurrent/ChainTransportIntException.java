package org.reminstant.concurrent;

public class ChainTransportIntException extends RuntimeException {

  private final int cargo;

  public ChainTransportIntException(int cargo) {
    super("Exception was supposed to be handled in ChainableFuture chain (cargo %d)".formatted(cargo));
    this.cargo = cargo;
  }

  public int getCargo() {
    return cargo;
  }
}
