package org.reminstant.cryptography;

@FunctionalInterface
public interface KeyScheduler {

  byte[][] schedule(byte[] key);
}
