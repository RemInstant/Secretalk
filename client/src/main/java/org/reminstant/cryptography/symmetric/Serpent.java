package org.reminstant.cryptography.symmetric;

import org.reminstant.cryptography.Bits;
import org.reminstant.cryptography.KeyScheduler;
import org.reminstant.cryptography.SymmetricCryptoSystem;

import java.util.List;
import java.util.stream.IntStream;

public final class Serpent implements SymmetricCryptoSystem {

  private static final int BLOCK_BYTE_SIZE = 16;
  private static final List<Integer> KEY_BYTE_SIZES = List.of(16, 24, 32);

  private static final int[][] S_BOXES = {
      {  3,  8, 15,  1, 10,  6,  5, 11, 14, 13,  4,  2,  7,  0,  9, 12 },
      { 15, 12,  2,  7,  9,  0,  5, 10,  1, 11, 14,  8,  6, 13,  3,  4 },
      {  8,  6,  7,  9,  3, 12, 10, 15, 13,  1, 14,  4,  0, 11,  5,  2 },
      {  0, 15, 11,  8, 12,  9,  6,  3, 13,  1,  2,  4, 10,  7,  5, 14 },
      {  1, 15,  8,  3, 12,  0, 11,  6,  2,  5,  4, 10,  9, 14,  7, 13 },
      { 15,  5,  2, 11,  4, 10,  9, 12,  0,  3, 14,  8, 13,  6,  7,  1 },
      {  7,  2, 12,  5,  8,  4,  6, 11, 14,  9,  1, 15, 13,  3, 10,  0 },
      {  1, 13, 15,  0, 14,  8,  2, 11,  7,  4, 12, 10,  9,  3,  5,  6 },
  };

  private static final int[][] INV_S_BOXES = {
      { 13,  3, 11,  0, 10,  6,  5, 12,  1, 14,  4,  7, 15,  9,  8,  2 },
      {  5,  8,  2, 14, 15,  6, 12,  3, 11,  4,  7,  9,  1, 13, 10,  0 },
      { 12,  9, 15,  4, 11, 14,  1,  2,  0,  3,  6, 13,  5,  8, 10,  7 },
      {  0,  9, 10,  7, 11, 14,  6, 13,  3,  5, 12,  2,  4,  8, 15,  1 },
      {  5,  0,  8,  3, 10,  9,  7, 14,  2, 12, 11,  6,  4, 15, 13,  1 },
      {  8, 15,  2,  9,  4,  1, 13, 14, 11,  6,  5,  3,  7, 12, 10,  0 },
      { 15, 10,  1, 13,  5,  3,  6,  0,  4,  9, 14,  7,  2, 12,  8, 11 },
      {  3,  0,  6, 13,  9, 14, 15,  8,  5, 12, 11,  7, 10,  1,  4,  2 },
  };

  private final KeyScheduler keyScheduler;
  private final int[][] roundKeys;


  public static List<Integer> getKeyByteSizes() {
    return KEY_BYTE_SIZES;
  }


  public Serpent(byte[] key) {
    this.keyScheduler = new Scheduler();
    byte[][] byteRoundKeys = keyScheduler.schedule(key);

    this.roundKeys = new int[byteRoundKeys.length][];
    IntStream.range(0, roundKeys.length).forEach(i ->
        roundKeys[i] = Bits.repackLittleEndianToInt(byteRoundKeys[i]));
  }

  @Override
  public byte[] encrypt(byte[] data) {
    if (data.length != BLOCK_BYTE_SIZE) {
      throw new IllegalArgumentException("Serpent crypto-system handles blocks of 128 bits");
    }

    int[] x = Bits.repackLittleEndianToInt(data);

    for (int i = 0; i < 31; ++i) {
      Bits.xorInPlace(x, roundKeys[i]);
      executeSBox(x, S_BOXES[i % S_BOXES.length]);
      executeLinearTransformation(x);
    }
    Bits.xorInPlace(x, roundKeys[31]);
    executeSBox(x, S_BOXES[7]);
    Bits.xorInPlace(x, roundKeys[32]);

    data = Bits.repackIntToLittleEndian(x);
    return data;
  }

  @Override
  public byte[] decrypt(byte[] data) {
    if (data.length != BLOCK_BYTE_SIZE) {
      throw new IllegalArgumentException("Serpent crypto-system handles blocks of 128 bits");
    }

    int[] x = Bits.repackLittleEndianToInt(data);

    Bits.xorInPlace(x, roundKeys[32]);
    executeSBox(x, INV_S_BOXES[7]);
    Bits.xorInPlace(x, roundKeys[31]);

    for (int i = 30; i >= 0; --i) {
      executeInvLinearTransformation(x);
      executeSBox(x, INV_S_BOXES[i % INV_S_BOXES.length]);
      Bits.xorInPlace(x, roundKeys[i]);
    }

    data = Bits.repackIntToLittleEndian(x);
    return data;
  }

  @Override
  public void setKey(byte[] key) {
    byte[][] byteRoundKeys = keyScheduler.schedule(key);
    IntStream.range(0, roundKeys.length).forEach(i ->
        roundKeys[i] = Bits.repackLittleEndianToInt(byteRoundKeys[i]));
  }

  @Override
  public int getBlockByteSize() {
    return BLOCK_BYTE_SIZE;
  }


  public static void executeSBox(int[] x, int[] sBox) {
    for (int i = 0; i < Integer.SIZE; ++i) {
      int x0Bit = (x[0] >>> (31 - i)) & 1;
      int x1Bit = (x[1] >>> (31 - i)) & 1;
      int x2Bit = (x[2] >>> (31 - i)) & 1;
      int x3Bit = (x[3] >>> (31 - i)) & 1;

      int key = x0Bit | (x1Bit << 1) | (x2Bit << 2) | (x3Bit << 3);
      int val = sBox[key];

      for (int j = 0; j < 4; ++j) {
        x[j] &= ~(1 << (31 - i));
        x[j] |= ((val >>> j) & 1) << (31 - i);
      }
    }
  }

//  public static void executeSBoxPerm(int[] x, int[] sBox) {
//    for (int i = 0; i < x.length; ++i) {
//      for (int j = 0; 4*j < Integer.SIZE; ++j) {
//        int key = (x[i] >>> (28 - 4*j)) & 0xF;
//        int val = sBox[key];
//        x[i] &= ~(0xF << (28 - 4*j));
//        x[i] |= val << (28 - 4*j);
//      }
//    }
//  }

  private static void executeLinearTransformation(int[] x) {
    x[0] = Integer.rotateLeft(x[0], 13);
    x[2] = Integer.rotateLeft(x[2], 3);
    x[1] = x[1] ^ x[0] ^ x[2];
    x[3] = x[3] ^ x[2] ^ (x[0] << 3);
    x[1] = Integer.rotateLeft(x[1], 1);
    x[3] = Integer.rotateLeft(x[3], 7);
    x[0] = x[0] ^ x[1] ^ x[3];
    x[2] = x[2]^ x[3] ^ (x[1] << 7);
    x[0] = Integer.rotateLeft(x[0], 5);
    x[2] = Integer.rotateLeft(x[2], 22);
  }

  private static void executeInvLinearTransformation(int[] x) {
    x[0] = Integer.rotateRight(x[0], 5);
    x[2] = Integer.rotateRight(x[2], 22);
    x[0] = x[0] ^ x[1] ^ x[3];
    x[2] = x[2]^ x[3] ^ (x[1] << 7);
    x[1] = Integer.rotateRight(x[1], 1);
    x[3] = Integer.rotateRight(x[3], 7);
    x[1] = x[1] ^ x[0] ^ x[2];
    x[3] = x[3] ^ x[2] ^ (x[0] << 3);
    x[0] = Integer.rotateRight(x[0], 13);
    x[2] = Integer.rotateRight(x[2], 3);
  }

  public static final class Scheduler implements KeyScheduler {

    private static final int PHI_CONST = 0x9E3779B9;

    @Override
    public byte[][] schedule(byte[] key) {
      if (!KEY_BYTE_SIZES.contains(key.length)) {
        throw new IllegalArgumentException("Serpent key scheduler handles keys of 128/192/256 bits");
      }

      int[] tmpKeys = new int[16];
      for (int i = 0; 4*i < key.length; ++i) {
        tmpKeys[i] = Bits.packLittleEndianToInt(key, 4*i);
      }
      if (key.length != 32) {
        tmpKeys[key.length / 4] = 1;
      }

      for (int i = 8; i < 16; ++i) {
        tmpKeys[i] = tmpKeys[i-8] ^ tmpKeys[i-5] ^ tmpKeys[i-3] ^ tmpKeys[i-1] ^ PHI_CONST ^ (i-8);
        tmpKeys[i] = Integer.rotateLeft(tmpKeys[i], 11);
      }

      int[] preKeys = new int[132];
      System.arraycopy(tmpKeys, 8, preKeys, 0, 8);

      for (int i = 8; i < preKeys.length; ++i) {
        preKeys[i] = preKeys[i-8] ^ preKeys[i-5] ^ preKeys[i-3] ^ preKeys[i-1] ^ PHI_CONST ^ i;
        preKeys[i] = Integer.rotateLeft(preKeys[i], 11);
      }

      int[] tmp = new int[4];
      for (int i = 0; 4*i < 132; ++i) {
        System.arraycopy(preKeys, 4*i, tmp, 0, tmp.length);
        executeSBox(tmp, S_BOXES[Math.floorMod(3 - i, S_BOXES.length)]);
        System.arraycopy(tmp, 0, preKeys, 4*i, tmp.length);
      }

      byte[][] keys = new byte[33][];
      for (int i = 0; i < keys.length; ++i) {
        keys[i] = new byte[16];
        Bits.unpackIntToLittleEndian(preKeys[4*i  ], keys[i], 0);
        Bits.unpackIntToLittleEndian(preKeys[4*i+1], keys[i], 4);
        Bits.unpackIntToLittleEndian(preKeys[4*i+2], keys[i], 8);
        Bits.unpackIntToLittleEndian(preKeys[4*i+3], keys[i], 12);
      }

      return keys;
    }
  }
}
