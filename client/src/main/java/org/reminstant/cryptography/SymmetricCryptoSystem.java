package org.reminstant.cryptography;

public interface SymmetricCryptoSystem {

  byte[] encrypt(byte[] data);

  byte[] decrypt(byte[] data);

  void setKey(byte[] key);

  int getBlockByteSize();
}
