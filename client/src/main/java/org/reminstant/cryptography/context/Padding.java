package org.reminstant.cryptography.context;

import org.reminstant.cryptography.Bits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

public enum Padding {

  NONE {
    @Override
    protected void fill(byte[] paddedBlock, int blockSize) {
      throw new UnsupportedOperationException("None-padding cannot set padding");
    }

    @Override
    protected int scan(byte[] block) {
      return 0;
    }

    @Override
    boolean isSetAlways() {
      return false;
    }
  },

  ZEROS {
    @Override
    protected void fill(byte[] paddedBlock, int blockSize) {
      Arrays.fill(paddedBlock, blockSize, paddedBlock.length, (byte) 0);
    }

    @Override
    protected int scan(byte[] block) {
      int byteToEraseCnt = 0;
      for (int i = block.length - 1; i >= 0 && block[i] == 0; --i) {
        byteToEraseCnt++;
      }
      return byteToEraseCnt;
    }
  },

  ANSI_X923 {
    @Override
    protected void fill(byte[] paddedBlock, int blockSize) {
      int addedBitsCount = paddedBlock.length - blockSize;
      paddedBlock[paddedBlock.length - 1] = (byte) addedBitsCount;
      Arrays.fill(paddedBlock, blockSize, paddedBlock.length - 1, (byte) 0);
    }

    @Override
    protected int scan(byte[] block) {
      return Byte.toUnsignedInt(block[block.length - 1]);
    }
  },

  PKCS7 {
    @Override
    protected void fill(byte[] paddedBlock, int blockSize) {
      int addedBitsCount = paddedBlock.length - blockSize;
      paddedBlock[paddedBlock.length - 1] = (byte) addedBitsCount;
      Arrays.fill(paddedBlock, blockSize, paddedBlock.length - 1, (byte) (addedBitsCount + 1));
    }

    @Override
    protected int scan(byte[] block) {
      return Byte.toUnsignedInt(block[block.length - 1]);
    }
  },

  ISO_10126 {
    @Override
    protected void fill(byte[] paddedBlock, int blockSize) {
      int addedBitsCount = paddedBlock.length - blockSize;
      paddedBlock[paddedBlock.length - 1] = (byte) addedBitsCount;
      IntStream.range(blockSize, paddedBlock.length - 1)
          .forEach(i -> paddedBlock[i] = (byte) RANDOM.nextInt());
    }

    @Override
    protected int scan(byte[] block) {
      return Byte.toUnsignedInt(block[block.length - 1]);
    }
  };


  private static final Logger LOGGER = LoggerFactory.getLogger(Padding.class);
  private static final Random RANDOM = new Random();


  protected abstract void fill(byte[] paddedBlock, int blockSize);

  protected abstract int scan(byte[] block);


  boolean isSetAlways() {
    return true;
  }

  byte[] setPadding(byte[] block, int blockByteSize) {
    if (block.length == blockByteSize) {
      return block;
    }
    byte[] paddedBlock = Arrays.copyOf(block, blockByteSize);
    fill(paddedBlock, block.length);
    return paddedBlock;
  }

  byte[] clearPadding(byte[] block) {
    int byteToEraseCnt = scan(block);
    if (byteToEraseCnt > block.length) {
      byteToEraseCnt = block.length;
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Block ({}) does not match the rules of padding", Bits.toHexString(block));
      }
    }
    return Arrays.copyOf(block, block.length - byteToEraseCnt);
  }
}