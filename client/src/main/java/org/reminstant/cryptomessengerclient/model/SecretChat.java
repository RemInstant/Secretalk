package org.reminstant.cryptomessengerclient.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@ToString
public class SecretChat {

  public enum State {
    PENDING,
    AWAITING,
    CONNECTED,
    DISCONNECTED,
//    REJECTED,
    DESERTED,
    DESTROYED
  }

  private final String id;
  private final String title;
  @Setter
  private State state;

  /**
   * <p> Purpose of this key depends on chat state:
   * <p> PENDING - key is private key of chat requester
   * <p> AWAITING - key is public key of chat requester
   * <p> CONNECTED - key is final secret key generated with Diffie-Hellman protocol
   * <p> DISCONNECTED, DESERTED, DESTROYED - key must be null
   */
  @Setter
  private BigInteger key;

  public SecretChat(String id, String title, State state) {
    this(id, title, state, null);
  }

  public SecretChat(String id, String title, State state, BigInteger key) {
    this.id = id;
    this.title = title;
    this.state = state;
    this.key = key;
  }
}
