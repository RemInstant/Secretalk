package org.reminstant.cryptography.symmetric;

import org.reminstant.cryptography.Bits;
import org.reminstant.cryptography.BitNumbering;
import org.reminstant.cryptography.CryptoOperation;
import org.reminstant.cryptography.KeyScheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

public final class DEAL extends FeistelNetwork {

  private static final int BLOCK_BYTE_SIZE = 16;


  public DEAL(byte[] key) {
    super(new Scheduler(), new FeistelFunction(), BLOCK_BYTE_SIZE, key);
  }


  @Override
  protected void executeBeforeNetwork(byte[] data, boolean isEncryption) {
    if (data.length != BLOCK_BYTE_SIZE) {
      throw new IllegalArgumentException("DEAL crypto-system handles blocks of 128 bits");
    }
    if (isEncryption) {
      swapHalves(data);
    }
  }

  @Override
  protected void executeAfterNetwork(byte[] data, boolean isEncryption) {
    if (!isEncryption) {
      swapHalves(data);
    }
  }


  private void swapHalves(byte[] data) {
    for (int i = 0; i < data.length / 2; ++i) {
      byte tmp = data[i];
      data[i] = data[data.length / 2 + i];
      data[data.length / 2 + i] = tmp;
    }
  }


  public static final class Scheduler implements KeyScheduler {

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

    private static final int[] KEY_1_EXTRACTOR = {
         1,  2,  3,  4,  5,  6,  7,  8,
         9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24,
        25, 26, 27, 28, 29, 30, 31, 32,
        33, 34, 35, 36, 37, 38, 39, 40,
        41, 42, 43, 44, 45, 46, 47, 48,
        49, 50, 51, 52, 53, 54, 55, 56,
        57, 58, 59, 60, 61, 62, 63, 64,
    };

    private static final int[] KEY_2_EXTRACTOR = {
         65,  66,  67,  68,  69,  70,  71,  72,
         73,  74,  75,  76,  77,  78,  79,  80,
         81,  82,  83,  84,  85,  86,  87,  88,
         89,  90,  91,  92,  93,  94,  95,  96,
         97,  98,  99, 100, 101, 102, 103, 104,
        105, 106, 107, 108, 109, 110, 111, 112,
        113, 114, 115, 116, 117, 118, 119, 120,
        121, 122, 123, 124, 125, 126, 127, 128,
    };

    private static final int[] KEY_3_EXTRACTOR = {
        129, 130, 131, 132, 133, 134, 135, 136,
        137, 138, 139, 140, 141, 142, 143, 144,
        145, 146, 147, 148, 149, 150, 151, 152,
        153, 154, 155, 156, 157, 158, 159, 160,
        161, 162, 163, 164, 165, 166, 167, 168,
        169, 170, 171, 172, 173, 174, 175, 176,
        177, 178, 179, 180, 181, 182, 183, 184,
        185, 186, 187, 188, 189, 190, 191, 192,
    };

    private static final int[] KEY_4_EXTRACTOR = {
        193, 194, 195, 196, 197, 198, 199, 200,
        201, 202, 203, 204, 205, 206, 207, 208,
        209, 210, 211, 212, 213, 214, 215, 216,
        217, 218, 219, 220, 221, 222, 223, 224,
        225, 226, 227, 228, 229, 230, 231, 232,
        233, 234, 235, 236, 237, 238, 239, 240,
        241, 242, 243, 244, 245, 246, 247, 248,
        249, 250, 251, 252, 253, 254, 255, 256,
    };

    private static final byte[] CONST_1 = Bits.split(1L << 63, 8);
    private static final byte[] CONST_2 = Bits.split(1L << 62, 8);
    private static final byte[] CONST_4 = Bits.split(1L << 60, 8);
    private static final byte[] CONST_8 = Bits.split(1L << 56, 8);

    @Override
    public byte[][] schedule(byte[] key) {
      byte[][] keys = extractKeys(key);
      byte[][] roundKeys = switch (key.length) {
        case 16 -> schedule128(keys);
        case 24 -> schedule192(keys);
        case 32 -> schedule256(keys);
        default -> throw new IllegalArgumentException("DEAL key scheduler handles keys of 128/192/256 bits");
      };
      IntStream.range(0, roundKeys.length)
          .forEach(i -> roundKeys[i] = Bits.permute(roundKeys[i], PARITY_BITS_ERASER, BitNumbering.MSB1));
      return roundKeys;
    }

    public byte[][] schedule128(byte[][] keys) {
      byte[][] roundKeys = new byte[6][];
      roundKeys[0] = DES_ENCRYPTOR.encrypt(keys[0]);
      roundKeys[1] = DES_ENCRYPTOR.encrypt(Bits.xor(keys[1], roundKeys[0]));
      roundKeys[2] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[0], CONST_1), roundKeys[1]));
      roundKeys[3] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[1], CONST_2), roundKeys[2]));
      roundKeys[4] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[0], CONST_4), roundKeys[3]));
      roundKeys[5] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[1], CONST_8), roundKeys[4]));
      return roundKeys;
    }

    public byte[][] schedule192(byte[][] keys) {
      byte[][] roundKeys = new byte[6][];
      roundKeys[0] = DES_ENCRYPTOR.encrypt(keys[0]);
      roundKeys[1] = DES_ENCRYPTOR.encrypt(Bits.xor(keys[1], roundKeys[0]));
      roundKeys[2] = DES_ENCRYPTOR.encrypt(Bits.xor(keys[2], roundKeys[1]));
      roundKeys[3] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[0], CONST_1), roundKeys[2]));
      roundKeys[4] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[1], CONST_2), roundKeys[3]));
      roundKeys[5] = DES_ENCRYPTOR.encrypt(Bits.xor(Bits.xor(keys[2], CONST_4), roundKeys[4]));
      return roundKeys;
    }

    public byte[][] schedule256(byte[][] keys) {
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

    private byte[][] extractKeys(byte[] key) {
      byte[][] keys = new byte[4][];
      if (key.length >= 16) {
        keys[0] = Bits.permute(key, KEY_1_EXTRACTOR, BitNumbering.MSB1);
        keys[1] = Bits.permute(key, KEY_2_EXTRACTOR, BitNumbering.MSB1);
      }
      if (key.length >= 24) {
        keys[2] = Bits.permute(key, KEY_3_EXTRACTOR, BitNumbering.MSB1);
      }
      if (key.length >= 32) {
        keys[3] = Bits.permute(key, KEY_4_EXTRACTOR, BitNumbering.MSB1);
      }
      return keys;
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
