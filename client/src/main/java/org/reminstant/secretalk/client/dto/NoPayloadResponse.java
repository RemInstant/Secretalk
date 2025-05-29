package org.reminstant.secretalk.client.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.reminstant.secretalk.client.util.ClientStatus;

import java.util.Date;

@RequiredArgsConstructor
@Getter
public class NoPayloadResponse {

  private final Date timestamp;
  private final int internalStatus;

  public NoPayloadResponse() {
    timestamp = null;
    internalStatus = 0;
  }
  
  public boolean isOk() {
    return internalStatus == ClientStatus.OK || internalStatus == 200;
  }
}
