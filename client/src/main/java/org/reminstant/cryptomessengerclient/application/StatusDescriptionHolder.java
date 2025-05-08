package org.reminstant.cryptomessengerclient.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
public class StatusDescriptionHolder {

  private final Map<String, Map<Object, String>> desc;

  public StatusDescriptionHolder(@Value("${status.description.path}") String path) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
      this.desc = new Yaml().load(stream);
    }
  }

  public String getDescription(int status) {
    return getDescription(status, "default");
  }

  public String getDescription(int status, String profile) {
    String res = getDescriptionInner(status, profile);
    if (res != null) {
      return res;
    }

    res = getDescriptionInner(status, "default");
    if (res != null) {
      return res;
    }

    return "Непредвиденная ошибка (код %d)".formatted(status);
  }

  private String getDescriptionInner(int status, String profile) {
    if (!desc.containsKey(profile)) {
      return null;
    }

    Map<Object, String> profileDesc = desc.get(profile);
//    String statusString = String.valueOf(status);
    String statusClassString = status / 100 + "xx";

    return profileDesc.getOrDefault(
        status,
        profileDesc.getOrDefault(statusClassString, null));
  }
}
