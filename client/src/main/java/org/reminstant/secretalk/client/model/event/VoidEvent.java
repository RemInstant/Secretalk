package org.reminstant.secretalk.client.model.event;

public class VoidEvent extends UserEvent {

  public static final String EVENT_NAME = "Void";

  static final VoidEvent instance = new VoidEvent();

  private VoidEvent() {
    super("");
  }
}
