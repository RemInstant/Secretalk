package org.reminstant.cryptomessengerserver.dto.common;

import java.util.Arrays;
import java.util.Objects;

public record ChatConfiguration(
    String title,
    String cryptoSystemName,
    String cipherMode,
    String paddingMode,
    byte[] initVector,
    byte[] randomDelta) {

  public ChatConfiguration() {
    this(null, null, null, null,
        null, null);
  }

  @SuppressWarnings("DeconstructionCanBeUsed")
  @Override
  public boolean equals(Object object) {
    return object instanceof ChatConfiguration config && // NOSONAR
        title.equals(config.title) &&
        cryptoSystemName.equals(config.cryptoSystemName) &&
        cipherMode.equals(config.cipherMode) &&
        paddingMode.equals(config.paddingMode) &&
        Arrays.equals(initVector, config.initVector) &&
        Arrays.equals(randomDelta, config.randomDelta);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(title);
    result = 31 * result + Objects.hashCode(cryptoSystemName);
    result = 31 * result + Objects.hashCode(cipherMode);
    result = 31 * result + Objects.hashCode(paddingMode);
    result = 31 * result + Arrays.hashCode(initVector);
    result = 31 * result + Arrays.hashCode(randomDelta);
    return result;
  }

  @Override
  public String toString() {
    return "ChatConfiguration{" +
        "title='" + title + '\'' +
        ", cryptoSystemName='" + cryptoSystemName + '\'' +
        ", cipherMode='" + cipherMode + '\'' +
        ", paddingMode='" + paddingMode + '\'' +
        ", initVector=" + Arrays.toString(initVector) +
        ", randomDelta=" + Arrays.toString(randomDelta) +
        '}';
  }
}
