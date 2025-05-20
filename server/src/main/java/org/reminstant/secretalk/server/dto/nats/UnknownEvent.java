package org.reminstant.secretalk.server.dto.nats;

public class UnknownEvent extends UserEvent {

  UnknownEvent(String id) {
    super(id);
  }

  UnknownEvent() {
    this("");
  }
}