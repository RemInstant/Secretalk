package org.reminstant.secretalk.client.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@ToString
public class Chat {

  public record Configuration(
      String title,
      String cryptoSystemName,
      String cipherMode,
      String paddingMode,
      byte[] initVector,
      byte[] randomDelta) {

    public Configuration() {
      this(null, null, null, null, null, null);
    }

    public Configuration(String title, String cryptoSystemName,
                         String cipherMode, String paddingMode) {
      this(title, cryptoSystemName, cipherMode, paddingMode, null, null);
    }

    public Configuration(Configuration other, byte[] initVector, byte[] randomDelta) {
      this(other.title, other.cryptoSystemName, other.cipherMode,
          other.paddingMode, initVector, randomDelta);
    }
  }

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
  private final String otherUsername;
  private final Configuration configuration;
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

  // TODO: kill this constructor
  public Chat(String id, String otherUsername, State state) {
    this(id,
        otherUsername,
        new Configuration(otherUsername, null,
            null, null, null, null),
        state,
        null);
  }

  public Chat(String id, String otherUsername, Configuration configuration,
              State state, BigInteger key) {
    this.id = id;
    this.otherUsername = otherUsername;
    this.configuration = configuration;
    this.state = state;
    this.key = key;
  }

  public String getTitle() {
    return configuration.title;
  }

  public String getCryptoSystemName() {
    return configuration.cryptoSystemName;
  }

  public String getCipherMode() {
    return configuration.cipherMode;
  }

  public String getPaddingMode() {
    return configuration.paddingMode;
  }

  public byte[] getInitVector() {
    return configuration.initVector;
  }

  public byte[] getRandomDelta() {
    return configuration.randomDelta;
  }
}
