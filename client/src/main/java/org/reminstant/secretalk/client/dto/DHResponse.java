package org.reminstant.secretalk.client.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class DHResponse extends NoPayloadResponse {

  private final String prime;
  private final String generator;

  public DHResponse() {
    super();
    prime = null;
    generator = null;
  }
}
