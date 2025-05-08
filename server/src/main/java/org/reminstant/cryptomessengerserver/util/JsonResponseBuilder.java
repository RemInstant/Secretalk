package org.reminstant.cryptomessengerserver.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;

public class JsonResponseBuilder {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final ObjectNode objectNode;

  private JsonResponseBuilder() {
    objectNode = OBJECT_MAPPER.createObjectNode();
  }

  public static JsonResponseBuilder status(int status) {
    JsonResponseBuilder builder = new JsonResponseBuilder();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    dateFormat.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
    String timestamp = dateFormat.format(new Date()).replace("Z", "+00:00");
    return builder
        .property("timestamp", timestamp)
        .property("status", String.valueOf(status));
  }

  public JsonResponseBuilder property(String property, String value) {
    objectNode.put(property, value);
    return this;
  }

  public String build() {
    return objectNode.toString();
  }
}