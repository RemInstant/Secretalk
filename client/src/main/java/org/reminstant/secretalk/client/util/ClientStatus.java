package org.reminstant.secretalk.client.util;

public class ClientStatus {

  public static final int OK = 200000;
  
  public static final int MODULE_INITIALISATION_FAILURE = 602001;
  public static final int MODULE_UNINITIALISED_ACCESS = 602002;
  public static final int NOTHING_TO_PROCESS = 602003;

  public static final int SERVER_CONNECTION_FAILURE = 603001;
  public static final int SERVER_RESPONSE_ERROR = 603002;
  public static final int SERVER_UNEXPECTED_RESPONSE = 603003;
  public static final int SERVER_RESPONSE_PARSE_FAILURE = 603004;

  public static final int CHAT_ILLEGAL_REQUEST = 604001;

  public static final int STORAGE_FAILURE = 605000;
  public static final int STORAGE_READ_FAILURE = 605001;
  public static final int STORAGE_WRITE_FAILURE = 605002;
  public static final int STORAGE_CREATION_FAILURE = 605003;
  public static final int STORAGE_TMP_CREATION_FAILURE = 605004;
  public static final int STORAGE_DELETION_FAILURE = 605005;

  private ClientStatus() {
  }

}
