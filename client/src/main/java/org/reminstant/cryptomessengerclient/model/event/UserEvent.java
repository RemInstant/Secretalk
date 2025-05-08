package org.reminstant.cryptomessengerclient.model.event;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Getter
public abstract class UserEvent {

  private final String id;

  protected UserEvent(String eventId) {
    Objects.requireNonNull(eventId, "eventId cannot be null");
    this.id = eventId;
  }

  public static UserEvent getEvent(String eventType, Map<String, String> data) {
    UserEvent event = switch (eventType) {
      case ChatConnectionRequestEvent.EVENT_NAME -> new ChatConnectionRequestEvent(data);
      case ChatConnectionAcceptEvent.EVENT_NAME -> new ChatConnectionAcceptEvent(data);
      case ChatConnectionBreakEvent.EVENT_NAME -> new ChatConnectionBreakEvent(data);
      case ChatDesertEvent.EVENT_NAME -> new ChatDesertEvent(data);
      case ChatDestroyEvent.EVENT_NAME -> new ChatDestroyEvent(data);
      case VoidEvent.EVENT_NAME -> VoidEvent.instance;
      default -> null;
    };

    if (event == null) {
      log.error("Got invalid event type ({})", eventType);
      event = new UnknownEvent(data);
    }

    return event;
  }
}