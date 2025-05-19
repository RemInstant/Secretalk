package org.reminstant.cryptomessengerclient.model.event;

public class UnknownEvent extends UserEvent {

  UnknownEvent(String id) {
    super(id);
  }

  UnknownEvent() {
    this("");
  }
}