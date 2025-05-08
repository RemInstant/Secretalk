package org.reminstant.cryptomessengerclient.dto;

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
