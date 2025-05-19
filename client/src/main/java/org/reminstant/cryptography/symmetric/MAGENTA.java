package org.reminstant.cryptography.symmetric;

import org.reminstant.cryptography.Bits;
import org.reminstant.cryptography.CryptoOperation;
import org.reminstant.cryptography.GaloisField256;

import java.util.List;

public final class MAGENTA extends FeistelNetwork {

  private static final int BLOCK_BYTE_SIZE = 16;
  private static final List<Integer> KEY_BYTE_SIZES = List.of(16, 24, 32);

  private static final byte PRIMITIVE_ELEMENT = 2;
  private static final byte GENERATING_POLYNOMIAL = (byte) 0x165; // x^8+x^6+x^5+x^2+1
  private static final int FUNCTION_E_ROUND_CNT = 3;

  private static final int[] SWAP_BLOCK_HALVES_RULE = {
      8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7
  };


  public static List<Integer> getKeyByteSizes() {
    return KEY_BYTE_SIZES;
  }


  public MAGENTA(byte[] key) {
    super(new Scheduler(), new FeistelFunction(), BLOCK_BYTE_SIZE, key);
  }


  @Override
  protected void executeBeforeNetwork(byte[] data, boolean isEncryption) {
    if (data.length != BLOCK_BYTE_SIZE) {
      throw new IllegalArgumentException("MAGENTA crypto-system handles blocks of 128 bits");
    }
    if (!isEncryption) {
      byte[] swapped = Bits.permuteBytes(data, SWAP_BLOCK_HALVES_RULE);
      System.arraycopy(swapped, 0, data, 0, data.length);
    }
  }

  @Override
  protected void executeAfterNetwork(byte[] data, boolean isEncryption) {
    if (isEncryption) {
      byte[] swapped = Bits.permuteBytes(data, SWAP_BLOCK_HALVES_RULE);
      System.arraycopy(swapped, 0, data, 0, data.length);
    }
  }


  public static final class Scheduler extends ExtractScheduler {

    private static final int SUBKEY_BYTE_SIZE = 8;
    private static final int MAX_SUBKEY_COUNT = 4;

    public Scheduler() {
      super(SUBKEY_BYTE_SIZE, MAX_SUBKEY_COUNT);
    }

    @Override
    public byte[][] schedule(byte[] key) {
      byte[][] keys = super.schedule(key);
      return switch (key.length) {
        case 16 -> schedule128(keys);
        case 24 -> schedule192(keys);
        case 32 -> schedule256(keys);
        default -> throw new IllegalArgumentException("MAGENTA key scheduler handles keys of 128/192/256 bits");
      };
    }

    private byte[][] schedule128(byte[][] keys) {
      return new byte[][]{
          keys[0], keys[0], keys[1], keys[1], keys[0], keys[0]
      };
    }

    private byte[][] schedule192(byte[][] keys) {
      return new byte[][]{
          keys[0], keys[1], keys[2], keys[2], keys[1], keys[0]
      };
    }

    private byte[][] schedule256(byte[][] keys) {
      return new byte[][]{
          keys[0], keys[1], keys[2], keys[3], keys[3], keys[2], keys[1], keys[0]
      };
    }
  }

  public static final class FeistelFunction implements CryptoOperation {

    private static final int[] LEFT_EXTRACTOR  = { 0, 1,  2,  3,  4,  5,  6,  7 };
    private static final int[] RIGHT_EXTRACTOR = { 8, 9, 10, 11, 12, 13, 14, 15 };
    private static final int[] EVEN_EXTRACTOR  = { 0, 2,  4,  6,  8, 10, 12, 14 };
    private static final int[] ODD_EXTRACTOR   = { 1, 3,  5,  7,  9, 11, 13, 15 };

    private final byte[] fTable;

    public FeistelFunction() {
      fTable = new byte[256];

      fTable[0] = 1;
      for (int i = 1; i < fTable.length - 1; ++i) {
        fTable[i] = GaloisField256.product(fTable[i-1], PRIMITIVE_ELEMENT, GENERATING_POLYNOMIAL);
      }
      fTable[fTable.length - 1] = 0;
    }

    @Override
    public byte[] apply(byte[] data, byte[] key) {
      return executeFunctionE(Bits.merge(data, key));
    }

    private byte executeFunctionA(byte x, byte y) {
      byte fy = fTable[Byte.toUnsignedInt(y)];
      return fTable[(x & 0xFF) ^ (fy & 0xFF)];
    }

    private byte[] executeFunctionP(byte[] x) {
      byte[] result = new byte[16];
      for (int i = 0; 2*i < result.length; ++i) {
        result[2*i  ] = executeFunctionA(x[i], x[i+8]);
        result[2*i+1] = executeFunctionA(x[i+8], x[i]);
      }
      return result;
    }

    private byte[] executeFunctionT(byte[] x) {
      x = executeFunctionP(x);
      x = executeFunctionP(x);
      x = executeFunctionP(x);
      x = executeFunctionP(x);
      return x;
    }

    private byte[] executeFunctionE(byte[] x) {
      byte[] c = executeFunctionT(x);
      for (int i = 1; i < FUNCTION_E_ROUND_CNT; ++i) {
        byte[] leftX = Bits.permuteBytes(x, LEFT_EXTRACTOR);
        byte[] rightX = Bits.permuteBytes(x, RIGHT_EXTRACTOR);
        byte[] evenC = Bits.permuteBytes(c, EVEN_EXTRACTOR);
        byte[] oddC = Bits.permuteBytes(c, ODD_EXTRACTOR);
        byte[] tInput = Bits.merge(Bits.xor(leftX, evenC), Bits.xor(rightX, oddC));
        c = executeFunctionT(tInput);
      }
      return Bits.permuteBytes(c, EVEN_EXTRACTOR);
    }
  }
}
