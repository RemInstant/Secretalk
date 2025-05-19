package org.reminstant.cryptography.symmetric;

import org.reminstant.cryptography.BitNumbering;
import org.reminstant.cryptography.Bits;
import org.reminstant.cryptography.CryptoOperation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

public final class DEAL extends FeistelNetwork {

  private static final int BLOCK_BYTE_SIZE = 16;
  private static final List<Integer> KEY_BYTE_SIZES = List.of(16, 24, 32);

  private static final int[] SWAP_BLOCK_HALVES_RULE = {
      8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7
  };


  public static List<Integer> getKeyByteSizes() {
    return KEY_BYTE_SIZES;
  }

  public DEAL(byte[] key) {
    super(new Scheduler(), new FeistelFunction(), BLOCK_BYTE_SIZE, key);
  }


  @Override
  protected void executeBeforeNetwork(byte[] data, boolean isEncryption) {
    if (data.length != BLOCK_BYTE_SIZE) {
      throw new IllegalArgumentException("DEAL crypto-system handles blocks of 128 bits");
    }
    if (isEncryption) {
      byte[] swapped = Bits.permuteBytes(data, SWAP_BLOCK_HALVES_RULE);
      System.arraycopy(swapped, 0, data, 0, data.length);
    }
  }

  @Override
  protected void executeAfterNetwork(byte[] data, boolean isEncryption) {
    if (!isEncryption) {
      byte[] swapped = Bits.permuteBytes(data, SWAP_BLOCK_HALVES_RULE);
      System.arraycopy(swapped, 0, data, 0, data.length);
    }
  }


  public static final class Scheduler extends ExtractScheduler {

    private static final int SUBKEY_BYTE_SIZE = 8;
    private static final int MAX_SUBKEY_COUNT = 4;

    // 64-bit version: 0x1234567890ABCDEF
    private static final byte[] DES_KEY_56 = {
        (byte) 0x12, (byte) 0x69, (byte) 0x5B, (byte) 0xC9, (byte) 0x15, (byte) 0x73, (byte) 0x77,
    };

    private static final DES DES_ENCRYPTOR = new DES(DES_KEY_56);

    private static final int[] PARITY_BITS_ERASER = {
         1,  2,  3,  4,  5,  6,  7,
         9, 10, 11, 12, 13, 14, 15,
        17, 18, 19, 20, 21, 22, 23,
        25, 26, 27, 28, 29, 30, 31,
        33, 34, 35, 36, 37, 38, 39,
        41, 42, 43, 44, 45, 46, 47,
        49, 50, 51, 52, 53, 54, 55,
        57, 58, 59, 60, 61, 62, 63,
    };

    private static final byte[] CONST_1 = Bits.unpackLongToBigEndian(1L << 63);
    private static final byte[] CONST_2 = Bits.unpackLongToBigEndian(1L << 62);
    private static final byte[] CONST_4 = Bits.unpackLongToBigEndian(1L << 60);
    private static final byte[] CONST_8 = Bits.unpackLongToBigEndian(1L << 56);

    public Scheduler() {
      super(SUBKEY_BYTE_SIZE, MAX_SUBKEY_COUNT);
    }

    @Override
    public byte[][] schedule(byte[] key) {
      byte[][] keys = super.schedule(key);
      byte[][] roundKeys = switch (key.length) {
        case 16 -> schedule128(keys);
        case 24 -> schedule192(keys);
        case 32 -> schedule256(keys);
        default -> throw new IllegalArgumentException("DEAL key scheduler handles keys of 128/192/256 bits");
      };
      IntStream.range(0, roundKeys.length)
          .forEach(i -> roundKeys[i] = Bits.permute(roundKeys[i], PARITY_BITS_ERASER, BitNumbering.MSB1_FIRST));
      return roundKeys;
    }

    private byte[][] schedule128(byte[][] keys) {
      byte[][] roundKeys = new byte[6][];
      roundKeys[0] = DES_ENCRYPTOR.encrypt(keys[0]);
      roundKeys[1] = DES_ENCRYPTOR.encrypt(Bits.xor(keys[1], roundKeys[0]));
      roundKeys[2] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[0], CONST_1), roundKeys[1]));
      roundKeys[3] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[1], CONST_2), roundKeys[2]));
      roundKeys[4] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[0], CONST_4), roundKeys[3]));
      roundKeys[5] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[1], CONST_8), roundKeys[4]));
      return roundKeys;
    }

    private byte[][] schedule192(byte[][] keys) {
      byte[][] roundKeys = new byte[6][];
      roundKeys[0] = DES_ENCRYPTOR.encrypt(keys[0]);
      roundKeys[1] = DES_ENCRYPTOR.encrypt(Bits.xor(keys[1], roundKeys[0]));
      roundKeys[2] = DES_ENCRYPTOR.encrypt(Bits.xor(keys[2], roundKeys[1]));
      roundKeys[3] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[0], CONST_1), roundKeys[2]));
      roundKeys[4] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[1], CONST_2), roundKeys[3]));
      roundKeys[5] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[2], CONST_4), roundKeys[4]));
      return roundKeys;
    }

    private byte[][] schedule256(byte[][] keys) {
      byte[][] roundKeys = new byte[8][];
      roundKeys[0] = DES_ENCRYPTOR.encrypt(keys[0]);
      roundKeys[1] = DES_ENCRYPTOR.encrypt(Bits.xor(keys[1], roundKeys[0]));
      roundKeys[2] = DES_ENCRYPTOR.encrypt(Bits.xor(keys[2], roundKeys[1]));
      roundKeys[3] = DES_ENCRYPTOR.encrypt(Bits.xor(keys[3], roundKeys[2]));
      roundKeys[4] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[0], CONST_1), roundKeys[3]));
      roundKeys[5] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[1], CONST_2), roundKeys[4]));
      roundKeys[6] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[2], CONST_4), roundKeys[5]));
      roundKeys[7] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[3], CONST_8), roundKeys[6]));
      return roundKeys;
    }
  }

  public static final class FeistelFunction implements CryptoOperation {

    private static final int MAX_CACHE_SIZE = 24;

    private final Map<byte[], DES> cache;

    public FeistelFunction() {
      cache = new ConcurrentHashMap<>();
    }

    @Override
    public byte[] apply(byte[] data, byte[] key) {
      DES des = cache.getOrDefault(key, null);
      if (des == null) {
        if (cache.size() >= MAX_CACHE_SIZE) {
          cache.clear();
        }
        des = new DES(key);
        cache.put(key, des);
      }
      return des.encrypt(data);
    }
  }
}
