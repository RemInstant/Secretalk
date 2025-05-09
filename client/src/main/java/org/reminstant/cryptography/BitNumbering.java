package org.reminstant.cryptography;

public enum BitNumbering {
  LSB0, // The bit numbering starts at zero for the least significant bit
  LSB1, // The bit numbering starts at one  for the least significant bit
  MSB0, // The bit numbering starts at zero for the most  significant bit
  MSB1; // The bit numbering starts at one  for the most  significant bit

  public boolean getBit(byte[] data, int index) {
    throwIfIndexOutOfBound(index, data.length);

    int byteIndex = getByteIndex(index, data.length);
    int byteShift = getByteShift(index);

    return (data[byteIndex] >>> byteShift & 1) == 1;
  }

  public void clearBit(byte[] data, int index) {
    throwIfIndexOutOfBound(index, data.length);

    int byteIndex = getByteIndex(index, data.length);
    int byteShift = getByteShift(index);

    data[byteIndex] &= (byte) ~(1 << byteShift);
  }

  public void setBit(byte[] data, int index) {
    throwIfIndexOutOfBound(index, data.length);

    int byteIndex = getByteIndex(index, data.length);
    int byteShift = getByteShift(index);

    data[byteIndex] |= (byte) (1 << byteShift);
  }

  public void toggleBit(byte[] data, int index) {
    throwIfIndexOutOfBound(index, data.length);

    int byteIndex = getByteIndex(index, data.length);
    int byteShift = getByteShift(index);

    data[byteIndex] ^= (byte) (1 << byteShift);
  }

  private void throwIfIndexOutOfBound(int index, int length) {
    if (this.equals(LSB0) || this.equals(MSB0)) {
      if (index < 0 || index >= 8 * length) {
        throw new IndexOutOfBoundsException(
            String.format("There is no %dth index in %s indexing (bit count = %d}", index, this, 8 * length));
      }
    } else {
      if (index <= 0 || index > 8 * length) {
        throw new IndexOutOfBoundsException(
            String.format("There is no %dth index in %s indexing (bit count = %d}", index, this, 8 * length));
      }
    }
  }

  private int getByteIndex(int index, int length) {
    return switch(this) {
      case LSB0 -> length - (index + 1) / Byte.SIZE;
      case LSB1 -> length - index / Byte.SIZE;
      case MSB0 -> index / Byte.SIZE;
      case MSB1 -> (index - 1) / Byte.SIZE;
    };
  }

  private int getByteShift(int index) {
    return switch(this) {
      case LSB0 -> index % Byte.SIZE;
      case LSB1 -> (index - 1) % Byte.SIZE;
      case MSB0 -> (Byte.SIZE - 1) - index % Byte.SIZE;
      case MSB1 -> (Byte.SIZE - 1) - (index - 1) % Byte.SIZE;
    };
  }
}
