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
  private final boolean isImage;
  @Setter
  private State state;

  public Message(String id, String text, String author, String fileName,
                 boolean isImage, boolean belongedToReceiver) {
    this(id, text, author, fileName, belongedToReceiver, null, false, State.NEW);
  }

  public Message(String id, String text, String author, String fileName,
                 boolean belongedToReceiver, Path filePath, boolean isImage) {
    this(id, text, author, fileName, belongedToReceiver, filePath, isImage, State.NEW);
  }

  public Message(String id, String text, String author, String fileName,
                 boolean belongedToReceiver, Path filePath, boolean isImage, State state) {
    this.id = id;
    this.text = text;
    this.author = author;
    this.fileName = fileName;
    this.belongedToReceiver = belongedToReceiver;
    this.filePath = filePath;
    this.isImage = isImage;
    this.state = state;
  }
}
