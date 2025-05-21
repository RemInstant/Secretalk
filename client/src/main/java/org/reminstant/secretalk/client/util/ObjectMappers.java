package org.reminstant.secretalk.client.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectMappers {

  public static final ObjectMapper defaultObjectMapper;
  public static final ObjectMapper bigNumberObjectMapper;

  static {
    defaultObjectMapper = new ObjectMapper();
    bigNumberObjectMapper = new ObjectMapper(JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder()
            .maxNumberLength(5000)
            .build())
        .build());
  }

  private ObjectMappers() {

  }

}
