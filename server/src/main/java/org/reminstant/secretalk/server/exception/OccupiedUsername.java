package org.reminstant.secretalk.server.exception;

public class OccupiedUsername extends Exception {
  public OccupiedUsername() {
    super("Username is already occupied");
  }
}
