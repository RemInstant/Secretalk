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
    UPLOADING,
    REQUESTING,
    DOWNLOADING,
    DECRYPTING,
    FAILED,
    CANCELLED,
    SENT
  }

  private final String id;
  private final String text;
  private final String author;
  private final String fileName;
  private final boolean belongedToReceiver;
  @Setter
  private Path filePath;
  @Setter
  private State state;

  public Message(String id, String text, String author, String fileName, boolean belongedToReceiver) {
    this(id, text, author, fileName, belongedToReceiver, null, State.NEW);
  }

  public Message(String id, String text, String author, String fileName,
                 boolean belongedToReceiver, Path filePath) {
    this(id, text, author, fileName, belongedToReceiver, filePath, State.NEW);
  }

  public Message(String id, String text, String author, String fileName,
                 boolean belongedToReceiver, Path filePath, State state) {
    this.id = id;
    this.text = text;
    this.author = author;
    this.fileName = fileName;
    this.belongedToReceiver = belongedToReceiver;
    this.filePath = filePath;
    this.state = state;
  }
}
