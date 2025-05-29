package org.reminstant.secretalk.server.dto.nats;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;

import static org.reminstant.secretalk.server.util.ObjectMappers.*;

@Slf4j
@Getter
public abstract class UserEvent {

  private final String id;

  protected UserEvent(String eventId) {
    Objects.requireNonNull(eventId, "eventId cannot be null");
    this.id = eventId;
  }

  public static VoidEvent getVoidEvent() {
    return VoidEvent.instance;
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
      case ChatImageEvent.EVENT_NAME -> defaultObjectMapper.readValue(data, ChatImageEvent.class);

      case VoidEvent.EVENT_NAME -> VoidEvent.instance;

      default -> null;
    };

    if (event == null) {
      log.error("Got invalid event type ({})", eventType);
      event = defaultObjectMapper.readValue(data, new TypeReference<UnknownEvent>() {});
    }

    return event;
  }

  public static String getEventType(UserEvent userEvent) {
    return switch (userEvent) {
      case ChatConnectionRequestEvent _ -> ChatConnectionRequestEvent.EVENT_NAME;
      case ChatConnectionAcceptEvent _ -> ChatConnectionAcceptEvent.EVENT_NAME;
      case ChatConnectionBreakEvent _ -> ChatConnectionBreakEvent.EVENT_NAME;
      case ChatDesertEvent _ -> ChatDesertEvent.EVENT_NAME;
      case ChatDestroyEvent _ -> ChatDestroyEvent.EVENT_NAME;
      case ChatMessageEvent _ -> ChatMessageEvent.EVENT_NAME;
      case ChatFileEvent _ -> ChatFileEvent.EVENT_NAME;
      case ChatImageEvent _ -> ChatImageEvent.EVENT_NAME;
      case VoidEvent _ -> VoidEvent.EVENT_NAME;
      default -> "none";
    };
  }
}