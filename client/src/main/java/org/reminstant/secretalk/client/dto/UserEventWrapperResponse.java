package org.reminstant.secretalk.client.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class UserEventWrapperResponse extends NoPayloadResponse {

  private final String eventType;
  private final String eventJson;

  public UserEventWrapperResponse() {
    super();
    eventType = null;
    eventJson = null;
  }
}
