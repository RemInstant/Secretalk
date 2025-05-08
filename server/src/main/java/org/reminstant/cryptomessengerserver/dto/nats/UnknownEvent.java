package org.reminstant.cryptomessengerserver.dto.nats;

import java.util.Map;

public class UnknownEvent extends UserEvent {

  UnknownEvent(String id) {
    super(id);
  }

  UnknownEvent(Map<String, String> data) {
    this(data.getOrDefault("id", null));
  }
}