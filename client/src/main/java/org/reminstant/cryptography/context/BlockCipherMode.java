package org.reminstant.cryptography.context;

public enum BlockCipherMode {
  ECB("Electronic codebook", false),
  CBC("Cipher block chaining", true),
  PCBC("Propagating cipher block chaining", true),
  CFB("Cipher feedback", true),
  OFB("Output feedback", true),
  CTR("Counter", true),
  RD("Random delta", true);

  private final String fullName;
  private final boolean isInitVectorRequired;

  BlockCipherMode(String fullName, boolean isInitVectorRequired) {
    this.fullName = fullName;
    this.isInitVectorRequired = isInitVectorRequired;
  }

  String getName() {
    return this.fullName;
  }

  boolean isInitVectorRequires() {
    return this.isInitVectorRequired;
  }
}
