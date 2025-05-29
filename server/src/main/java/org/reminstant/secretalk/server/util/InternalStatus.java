package org.reminstant.secretalk.server.util;

public class InternalStatus {

  public static final int OK = 200000;
  public static final int INVALID_CREDENTIALS = 400001;
  public static final int INVALID_USERNAME = 400002;
  public static final int INVALID_PASSWORD = 400003;
  public static final int OCCUPIED_USERNAME = 400004;
  public static final int NON_EXISTENT_USER = 400010;
  public static final int SELF_REQUEST = 400011;
  public static final int TO_MUCH_DATA = 400012;
  public static final int RESOURCE_NOT_FOUND = 400020;

  public static int toHttpStatus(int internalStatus) {
    return internalStatus / 1000;
  }

  public static int fromHttpStatus(int httpStatus) {
    return httpStatus * 1000;
  }

  private InternalStatus() {

  }

}
