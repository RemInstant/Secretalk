package org.reminstant.cryptomessengerclient.model.event;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import static org.reminstant.cryptomessengerclient.util.ObjectMappers.defaultObjectMapper;

@Slf4j
@Getter
public abstract class UserEvent {

  private final String id;

  protected UserEvent(String eventId) {
    Objects.requireNonNull(eventId, "eventId cannot be null");
    this.id = eventId;
  }

  public static UserEvent getEvent(String eventType, byte[] data) throws IOException {
    UserEvent event = switch (eventType) {
      case ChatConnectionRequestEvent.EVENT_NAME -> defaultObjectMapper
          .readValue(data, ChatConnectionRequestEvent.class);
      case ChatConnectionAcceptEvent.EVENT_NAME -> defaultObjectMapper
          .readValue(data, ChatConnectionAcceptEvent.class);
      case ChatConnectionBreakEvent.EVENT_NAME -> defaultObjectMapper
          .readValue(data, ChatConnectionBreakEvent.class);
      case ChatDesertEvent.EVENT_NAME -> defaultObjectMapper.readValue(data, ChatDesertEvent.class);
      case ChatDestroyEvent.EVENT_NAME -> defaultObjectMapper.readValue(data, ChatDestroyEvent.class);
      case ChatMessageEvent.EVENT_NAME -> defaultObjectMapper.readValue(data, ChatMessageEvent.class);
      case ChatFileEvent.EVENT_NAME -> defaultObjectMapper.readValue(data, ChatFileEvent.class);

      case VoidEvent.EVENT_NAME -> VoidEvent.instance;

      default -> null;
    };

    if (event == null) {
      log.error("Got invalid event type ({})", eventType);
      event = defaultObjectMapper.readValue(data, new TypeReference<UnknownEvent>() {});
    }

    return event;
  }
}