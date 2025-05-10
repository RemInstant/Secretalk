package org.reminstant.cryptomessengerclient.model;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Message {

  private final String text;
  private final String author;
  private final boolean belongedToReceiver;

  public Message(String text, String author, boolean belongedToReceiver) {
    this.text = text;
    this.author = author;
    this.belongedToReceiver = belongedToReceiver;
  }

}
