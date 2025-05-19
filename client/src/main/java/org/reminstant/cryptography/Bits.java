package org.reminstant.cryptography;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Bits {
  private Bits() {
  }

  public static String toHexString(byte[] data) {
    StringBuilder builder = new StringBuilder();
    Stream<String> hexData = IntStream.range(0, data.length).mapToObj(i -> String.format("%02x", data[i]));
    hexData.forEach(builder::append);
    return builder.toString().toUpperCase();
  }

  public static byte[] fromHexString(String str) {
    if (str.length() % 2 != 0) {
      throw new IllegalArgumentException("Invalid hex representation of data");
    }
    byte[] data = new byte[str.length() / 2];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) Integer.parseInt(str.substring(2 * i, 2 * i + 2), 16);
    }
    return data;
  }

  public static byte[] permute(byte[] data, int[] permutationRule, BitNumbering numbering) {
    byte[] res = new byte[permutationRule.length / Byte.SIZE];
    int setShift = (numbering == BitNumbering.LSB1_FIRST || numbering == BitNumbering.MSB1_FIRST) ? 1 : 0;
    for(int i = 0; i < permutationRule.length; ++i) {
      try {
        if (numbering.getBit(data, permutationRule[i])) {
          numbering.setBit(res, i + setShift);
        }
      } catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Arguments are inconsistent", e);
      }
    }
    return res;
  }

  public static byte[] permuteBytes(byte[] data, int[] permutationRule) {
    byte[] res = new byte[permutationRule.length];
    for(int i = 0; i < permutationRule.length; ++i) {
      try {
        res[i] = data[permutationRule[i]];
      } catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Arguments are inconsistent", e);
      }
    }
    return res;
  }

  public static byte[] merge(byte[] bytesLeft, byte[] bytesRight) {
    byte[] res = Arrays.copyOf(bytesLeft, bytesLeft.length + bytesRight.length);
    System.arraycopy(bytesRight, 0, res, bytesLeft.length, bytesRight.length);
    return res;
  }

  public static byte[] xor(byte[] lhs, byte[] rhs) {
    if (lhs.length != rhs.length) {
      throw new IllegalArgumentException("XOR arguments have different length");
    } else {
      byte[] res = new byte[lhs.length];
      IntStream.range(0, res.length).forEach(i ->
          res[i] = (byte)(Byte.toUnsignedInt(lhs[i]) ^ Byte.toUnsignedInt(rhs[i])));
      return res;
    }
  }

  public static void xorInPlace(byte[] lhs, byte[] rhs) {
    if (lhs.length != rhs.length) {
      throw new IllegalArgumentException("XOR arguments have different length");
    } else {
      IntStream.range(0, lhs.length).forEach(i ->
          lhs[i] = (byte)(Byte.toUnsignedInt(lhs[i]) ^ Byte.toUnsignedInt(rhs[i])));
    }
  }

  public static void xorInPlace(int[] lhs, int[] rhs) {
    if (lhs.length != rhs.length) {
      throw new IllegalArgumentException("XOR arguments have different length");
    } else {
      IntStream.range(0, lhs.length).forEach(i -> lhs[i] = (lhs[i] ^ rhs[i]));
    }
  }

  // --- PACKING byte[] -> int/long ---

  public static long packToLong(byte[] bytes) {
    if (bytes.length > 8) {
      throw new IllegalArgumentException("Too much bytes for long");
    } else {
      long res = 0L;
      for(int i = 0; i < bytes.length; ++i) {
        res += Byte.toUnsignedLong(bytes[i]) << 8 * (bytes.length - 1 - i);
      }
      return res;
    }
  }

  public static int packBigEndianToInt(byte[] bytes, int offset) {
    if (offset + 3 >= bytes.length) {
      throw new IllegalArgumentException("Not enough bytes for int");
    }
    int res = Byte.toUnsignedInt(bytes[offset]) << 24;
    res += Byte.toUnsignedInt(bytes[offset + 1]) << 16;
    res += Byte.toUnsignedInt(bytes[offset + 2]) << 8;
    res += Byte.toUnsignedInt(bytes[offset + 3]);
    return res;
  }

  public static long packBigEndianToLong(byte[] bytes, int offset) {
    if (offset + 7 >= bytes.length) {
      throw new IllegalArgumentException("Not enough bytes for long");
    }
    int highPart = packBigEndianToInt(bytes, offset);
    int lowPart = packBigEndianToInt(bytes, offset + 4);
    return Integer.toUnsignedLong(highPart) << 32 | lowPart;
  }

  public static int packLittleEndianToInt(byte[] bytes, int offset) {
    if (offset + 3 >= bytes.length) {
      throw new IllegalArgumentException("Not enough bytes for int");
    }
    int res = Byte.toUnsignedInt(bytes[offset]);
    res += Byte.toUnsignedInt(bytes[offset + 1]) << 8;
    res += Byte.toUnsignedInt(bytes[offset + 2]) << 16;
    res += Byte.toUnsignedInt(bytes[offset + 3]) << 24;
    return res;
  }

  public static long packLittleEndianToLong(byte[] bytes, int offset) {
    if (offset + 7 >= bytes.length) {
      throw new IllegalArgumentException("Not enough bytes for long");
    }
    int highPart = packBigEndianToInt(bytes, offset + 4);
    int lowPart = packBigEndianToInt(bytes, offset);
    return Integer.toUnsignedLong(highPart) << 32 | lowPart;
  }

  // --- UNPACKING int/long -> byte[] ---

  public static byte[] unpackLong(long value, int byteCount) {
    if (Long.numberOfTrailingZeros(Long.highestOneBit(value)) + 1 > 8 * byteCount) {
      throw new IllegalArgumentException(
          String.format("Number %d cannot be presented as %d bytes", value, byteCount));
    } else {
      byte[] res = new byte[byteCount];
      for(int i = 0; i < byteCount; ++i) {
        res[i] = (byte)((int)(value >>> 8 * (byteCount - 1 - i)));
      }
      return res;
    }
  }

  public static void unpackIntToBigEndian(int value, byte[] bytes, int offset) {
    if (offset + 3 >= bytes.length) {
      throw new IllegalArgumentException("Not enough space to unpack");
    }
    bytes[offset    ] = (byte) (value >>> 24);
    bytes[offset + 1] = (byte) (value >>> 16);
    bytes[offset + 2] = (byte) (value >>> 8);
    bytes[offset + 3] = (byte) value;
  }

  public static void unpackLongToBigEndian(long value, byte[] bytes, int offset) {
    if (offset + 7 >= bytes.length) {
      throw new IllegalArgumentException("Not enough space to unpack");
    }
    unpackIntToBigEndian((int) (value >>> 32), bytes, 0);
    unpackIntToBigEndian((int) value, bytes, 4);
  }

  public static byte[] unpackIntToBigEndian(int value) {
    byte[] res = new byte[4];
    unpackIntToBigEndian(value, res, 0);
    return res;
  }

  public static byte[] unpackLongToBigEndian(long value) {
    byte[] res = new byte[8];
    unpackLongToBigEndian(value, res, 0);
    return res;
  }

  public static void unpackIntToLittleEndian(int value, byte[] bytes, int offset) {
    if (offset + 3 >= bytes.length) {
      throw new IllegalArgumentException("Not enough space to unpack");
    }
    bytes[offset    ] = (byte) value;
    bytes[offset + 1] = (byte) (value >>> 8);
    bytes[offset + 2] = (byte) (value >>> 16);
    bytes[offset + 3] = (byte) (value >>> 24);
  }

  public static void unpackLongToLittleEndian(long value, byte[] bytes, int offset) {
    if (offset + 7 >= bytes.length) {
      throw new IllegalArgumentException("Not enough space to unpack");
    }
    unpackIntToLittleEndian((int) value, bytes, 0);
    unpackIntToLittleEndian((int) (value >>> 32), bytes, 4);
  }

  public static byte[] unpackIntToLittleEndian(int value) {
    byte[] res = new byte[4];
    unpackIntToLittleEndian(value, res, 0);
    return res;
  }

  public static byte[] unpackLongToLittleEndian(long value) {
    byte[] res = new byte[8];
    unpackLongToLittleEndian(value, res, 0);
    return res;
  }

  // --- REPACKING byte[] <-> int[] ---

  public static int[] repackBigEndianToInt(byte[] data) {
    if (data.length % 4 != 0) {
      throw new IllegalArgumentException("data length must be multiple of 4");
    }
    int[] res = new int[data.length / 4];
    IntStream.range(0, res.length).forEach(i -> res[i] = packBigEndianToInt(data, 4*i));
    return res;
  }

  public static byte[] repackIntToBigEndian(int[] data) {
    byte[] res = new byte[data.length * 4];
    IntStream.range(0, data.length).forEach(i -> unpackIntToBigEndian(data[i], res, 4*i));
    return res;
  }

  public static int[] repackLittleEndianToInt(byte[] data) {
    if (data.length % 4 != 0) {
      throw new IllegalArgumentException("data length must be multiple of 4");
    }
    int[] res = new int[data.length / 4];
    IntStream.range(0, res.length).forEach(i -> res[i] = packLittleEndianToInt(data, 4*i));
    return res;
  }

  public static byte[] repackIntToLittleEndian(int[] data) {
    byte[] res = new byte[data.length * 4];
    IntStream.range(0, data.length).forEach(i -> unpackIntToLittleEndian(data[i], res, 4*i));
    return res;
  }
}
