package org.reminstant.secretalk.client.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

@Getter
@ToString
public class Message {

  public enum State {
    NEW,
    ENCRYPTING,
    TRANSMITTING,
    DECRYPTING,
    FAILED,
    CANCELLED,
    SENT
  }

  private final String id;
  private final String text;
  private final String author;
  private final Path filePath;
  private final boolean belongedToReceiver;
  @Setter
  private State state;

  public Message(String id, String text, String author, boolean belongedToReceiver) {
    this(id, text, author, null, belongedToReceiver, State.NEW);
  }

  public Message(String id, String text, String author, Path filePath, boolean belongedToReceiver) {
    this(id, text, author, filePath, belongedToReceiver, State.NEW);
  }

  public Message(String id, String text, String author, Path filePath, boolean belongedToReceiver, State state) {
    this.id = id;
    this.text = text;
    this.author = author;
    this.filePath = filePath;
    this.belongedToReceiver = belongedToReceiver;
    this.state = state;
  }
}
