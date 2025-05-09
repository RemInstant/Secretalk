package org.reminstant.cryptography;

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

  public static byte[] permute(byte[] data, int[] permutationRule, BitNumbering indexing) {
    byte[] res = new byte[permutationRule.length / 8];
    for(int i = 0; i < permutationRule.length; ++i) {
      try {
        if (indexing.getBit(data, permutationRule[i])) {
          indexing.setBit(res, i + 1);
        }
      } catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Arguments are inconsistent", e);
      }
    }
    return res;
  }

  public static byte[] split(long number) {
    byte[] res = new byte[8];
    for(int i = 0; i < 8; ++i) {
      res[i] = (byte)((int)(number >>> 8 * (7 - i)));
    }
    return res;
  }

  public static byte[] split(long number, int byteCount) {
    if (Long.numberOfTrailingZeros(Long.highestOneBit(number)) + 1 > 8 * byteCount) {
      throw new IllegalArgumentException(String.format("Number %d cannot be presented as %d bytes", number, byteCount));
    } else {
      byte[] res = new byte[byteCount];
      for(int i = 0; i < byteCount; ++i) {
        res[i] = (byte)((int)(number >>> 8 * (byteCount - 1 - i)));
      }
      return res;
    }
  }

  public static long merge(byte[] bytes) {
    if (bytes.length > 8) {
      throw new IllegalArgumentException("Long cannot contains the given bytes");
    } else {
      long res = 0L;
      for(int i = 0; i < bytes.length; ++i) {
        res += Byte.toUnsignedLong(bytes[i]) << 8 * (bytes.length - 1 - i);
      }
      return res;
    }
  }

  public static byte[] or(byte[] lhs, byte[] rhs) {
    if (lhs.length != rhs.length) {
      throw new IllegalArgumentException("OR arguments have different length");
    } else {
      byte[] res = new byte[lhs.length];
      IntStream.range(0, res.length).forEach(i -> res[i] = (byte)(Byte.toUnsignedInt(lhs[i]) | Byte.toUnsignedInt(rhs[i])));
      return res;
    }
  }

  public static byte[] and(byte[] lhs, byte[] rhs) {
    if (lhs.length != rhs.length) {
      throw new IllegalArgumentException("AND arguments have different length");
    } else {
      byte[] res = new byte[lhs.length];
      IntStream.range(0, res.length).forEach(i -> res[i] = (byte)(Byte.toUnsignedInt(lhs[i]) & Byte.toUnsignedInt(rhs[i])));
      return res;
    }
  }

  public static byte[] xor(byte[] lhs, byte[] rhs) {
    if (lhs.length != rhs.length) {
      throw new IllegalArgumentException("XOR arguments have different length");
    } else {
      byte[] res = new byte[lhs.length];
      IntStream.range(0, res.length).forEach(i -> res[i] = (byte)(Byte.toUnsignedInt(lhs[i]) ^ Byte.toUnsignedInt(rhs[i])));
      return res;
    }
  }
}
