package org.reminstant.cryptography.symmetric;

import org.reminstant.cryptography.Bits;
import org.reminstant.cryptography.CryptoOperation;
import org.reminstant.cryptography.KeyScheduler;
import org.reminstant.cryptography.SymmetricCryptoSystem;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

public abstract class FeistelNetwork implements SymmetricCryptoSystem {

  private final KeyScheduler keyScheduler;
  private final CryptoOperation roundFunction;
  private final int blockByteSize;
  private byte[][] roundKeys;

  private final int[] extractLeftPartRule;
  private final int[] extractRightPartRule;

  public FeistelNetwork(KeyScheduler keyScheduler, CryptoOperation roundFunction,
                        int blockByteSize, byte[] key) {
    if (keyScheduler == null || roundFunction == null) {
      throw new IllegalArgumentException("keyScheduler and roundFunction must be non-null");
    }
    if (blockByteSize % 2 == 1) {
      throw new IllegalArgumentException("Feistel network handles blocks of even byte-size");
    }

    this.keyScheduler = keyScheduler;
    this.roundFunction = roundFunction;
    this.blockByteSize = blockByteSize;
    this.roundKeys = keyScheduler.schedule(key);
    this.extractLeftPartRule = new int[blockByteSize / 2];
    this.extractRightPartRule = new int[blockByteSize / 2];

    for (int i = 0; i < blockByteSize / 2; ++i) {
      extractLeftPartRule[i] = i;
      extractRightPartRule[i] = blockByteSize / 2 + i;
    }
  }

  @Override
  public final byte[] encrypt(byte[] data) {
    byte[] res = Arrays.copyOf(data, data.length);
    executeBeforeNetwork(res, true);
    executeNetwork(res, IntUnaryOperator.identity());
    executeAfterNetwork(res, true);
    return res;
  }

  @Override
  public final byte[] decrypt(byte[] data) {
    byte[] res = Arrays.copyOf(data, data.length);
    executeBeforeNetwork(res, false);
    executeNetwork(res, i -> roundKeys.length - 1 - i);
    executeAfterNetwork(res, false);
    return res;
  }

  @Override
  public final void setKey(byte[] key) {
    this.roundKeys = keyScheduler.schedule(key);
  }

  @Override
  public final int getBlockByteSize() {
    return blockByteSize;
  }

  @SuppressWarnings("unused")
  protected void executeBeforeNetwork(byte[] data, boolean isEncryption) {
    // Override in subclasses
  }

  @SuppressWarnings("unused")
  protected void executeAfterNetwork(byte[] data, boolean isEncryption) {
    // Override in subclasses
  }


  private void executeNetwork(byte[] data, IntUnaryOperator keyIndexSelector) {
    if (data.length != blockByteSize) {
      throw new IllegalArgumentException(
          String.format("This Feistel network instance handles blocks of %s byte-size", blockByteSize));
    }

    byte[] leftPart = Bits.permuteBytes(data, extractLeftPartRule);
    byte[] rightPart = Bits.permuteBytes(data, extractRightPartRule);

    for (int i = 0; i < roundKeys.length; ++i) {
      int keyIndex = keyIndexSelector.applyAsInt(i);
      byte[] functionValue = roundFunction.apply(rightPart, roundKeys[keyIndex]);
      byte[] tmp = leftPart;
      leftPart = rightPart;
      rightPart = Bits.xor(tmp, functionValue);
    }

    System.arraycopy(Bits.merge(rightPart, leftPart), 0, data, 0, data.length);
  }
}
