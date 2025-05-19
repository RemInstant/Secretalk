package org.reminstant.cryptography.symmetric;

import org.reminstant.cryptography.Bits;
import org.reminstant.cryptography.KeyScheduler;

class ExtractScheduler implements KeyScheduler {

  private final int partSize;
  private final int[][] extractRules;

  public ExtractScheduler(int partByteSize, int maxPartCount) {
    if (maxPartCount <= 0) {
      throw new IllegalArgumentException("maxPartCount must be positive");
    }
    this.partSize = partByteSize;
    extractRules = new int[maxPartCount][];
    for (int i = 0; i < maxPartCount; ++i) {
      extractRules[i] = new int[partByteSize];
      for (int j = 0; j < partByteSize; ++j) {
        extractRules[i][j] = partByteSize * i + j;
      }
    }
  }

  @Override
  public byte[][] schedule(byte[] key) {
    int partCnt = Math.min(key.length / partSize, extractRules.length);
    byte[][] keys = new byte[partCnt][];
    for (int i = 0; i < partCnt; ++i) {
      keys[i] = Bits.permuteBytes(key, extractRules[i]);
    }
    return keys;
  }
}
