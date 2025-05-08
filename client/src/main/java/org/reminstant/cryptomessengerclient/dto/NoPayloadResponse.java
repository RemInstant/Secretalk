package org.reminstant.cryptomessengerclient.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@RequiredArgsConstructor
@Getter
public class NoPayloadResponse {

  private final Date timestamp;
  private final int status;

  public NoPayloadResponse() {
    timestamp = null;
    status = 0;
  }
}
