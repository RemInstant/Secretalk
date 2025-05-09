package org.reminstant.cryptography;

@FunctionalInterface
public interface CryptoOperation {

  byte[] apply(byte[] data, byte[] key);
}
