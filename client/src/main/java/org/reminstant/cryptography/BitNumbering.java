package org.reminstant.cryptography;

public enum BitNumbering {
  LSB0_FIRST, // The bit numbering starts at zero for the least significant bit
  LSB1_FIRST, // The bit numbering starts at one  for the least significant bit
  MSB0_FIRST, // The bit numbering starts at zero for the most  significant bit
  MSB1_FIRST; // The bit numbering starts at one  for the most  significant bit

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
    if (this.equals(LSB0_FIRST) || this.equals(MSB0_FIRST)) {
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
      case LSB0_FIRST -> length - 1 - index / Byte.SIZE;
      case LSB1_FIRST -> length - 1 - (index - 1) / Byte.SIZE;
      case MSB0_FIRST -> index / Byte.SIZE;
      case MSB1_FIRST -> (index - 1) / Byte.SIZE;
    };
  }

  private int getByteShift(int index) {
    return switch(this) {
      case LSB0_FIRST -> index % Byte.SIZE;
      case LSB1_FIRST -> (index - 1) % Byte.SIZE;
      case MSB0_FIRST -> (Byte.SIZE - 1) - index % Byte.SIZE;
      case MSB1_FIRST -> (Byte.SIZE - 1) - (index - 1) % Byte.SIZE;
    };
  }
}
