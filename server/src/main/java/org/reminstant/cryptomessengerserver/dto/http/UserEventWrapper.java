package org.reminstant.cryptomessengerserver.dto.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.cryptomessengerserver.dto.nats.*;

@Slf4j
@Getter
public class UserEventWrapper {

  private final String eventType;
  private final String eventJson;

  public UserEventWrapper(UserEvent userEvent) {
    eventType = UserEvent.getEventType(userEvent);
    try {
      eventJson = new ObjectMapper().writeValueAsString(userEvent);
    } catch (JsonProcessingException ex) {
      log.error("Failed to jsonify UserEvent subclass");
      throw new RuntimeException(ex);
    }
  }
}