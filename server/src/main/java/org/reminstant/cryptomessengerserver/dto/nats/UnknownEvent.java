package org.reminstant.cryptomessengerserver.dto.nats;

public class UnknownEvent extends UserEvent {

  UnknownEvent(String id) {
    super(id);
  }

  UnknownEvent() {
    this("");
  }
}