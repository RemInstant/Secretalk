package org.reminstant.cryptomessengerserver.exception;

public class OccupiedUsername extends Exception {
  public OccupiedUsername() {
    super("Username is already occupied");
  }
}
