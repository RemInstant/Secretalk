package org.reminstant.secretalk.client.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class JwtResponse extends NoPayloadResponse {

  private final String token;

  public JwtResponse() {
    super();
    token = null;
  }
}
