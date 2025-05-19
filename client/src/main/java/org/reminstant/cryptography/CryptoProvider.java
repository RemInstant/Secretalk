package org.reminstant.cryptography;

import org.reminstant.cryptography.context.BlockCipherMode;
import org.reminstant.cryptography.context.Padding;
import org.reminstant.cryptography.context.SymmetricCryptoContext;
import org.reminstant.cryptography.symmetric.DEAL;
import org.reminstant.cryptography.symmetric.DES;
import org.reminstant.cryptography.symmetric.MAGENTA;
import org.reminstant.cryptography.symmetric.Serpent;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CryptoProvider {

  private static final Random RANDOM = new SecureRandom();

  private static final Map<String, SymmetricCryptoSystem> MODEL_INSTANCES = new ConcurrentHashMap<>();

  private CryptoProvider() {

  }

  public static List<Integer> getKeySizes(String cryptoSystemName) {
    return switch (cryptoSystemName) {
      case "DES" -> DES.getKeyByteSizes();
      case "DEAL" -> DEAL.getKeyByteSizes();
      case "MAGENTA" -> MAGENTA.getKeyByteSizes();
      case "Serpent" -> Serpent.getKeyByteSizes();
      default -> throw new IllegalArgumentException("No such algorithm");
    };
  }

  public static int getBlockSize(String cryptoSystemName) {
    SymmetricCryptoSystem cryptoSystem = switch (cryptoSystemName) {
      case "DES" -> MODEL_INSTANCES.computeIfAbsent(cryptoSystemName, _ ->
          new DES(new byte[getKeySizes(cryptoSystemName).getFirst()]));
      case "DEAL" -> MODEL_INSTANCES.computeIfAbsent(cryptoSystemName, _ ->
          new DEAL(new byte[getKeySizes(cryptoSystemName).getFirst()]));
      case "MAGENTA" -> MODEL_INSTANCES.computeIfAbsent(cryptoSystemName, _ ->
          new MAGENTA(new byte[getKeySizes(cryptoSystemName).getFirst()]));
      case "Serpent" -> MODEL_INSTANCES.computeIfAbsent(cryptoSystemName, _ ->
          new Serpent(new byte[getKeySizes(cryptoSystemName).getFirst()]));
      default -> throw new IllegalArgumentException("No such symmetric algorithm");
    };
    return cryptoSystem.getBlockByteSize();
  }

  public static SymmetricCryptoSystem getCryptoSystem(String cryptoSystemName, byte[] key) {
    return switch (cryptoSystemName) {
      case "DES" -> new DES(key);
      case "DEAL" -> new DEAL(key);
      case "MAGENTA" -> new MAGENTA(key);
      case "Serpent" -> new Serpent(key);
      default -> throw new IllegalArgumentException("No such algorithm");
    };
  }

  public static byte[] generateInitVector(String cryptoSystemName) {
    byte[] initVector = new byte[getBlockSize(cryptoSystemName)];
    RANDOM.nextBytes(initVector);
    return initVector;
  }

  public static BigInteger generateRandomDelta(String cryptoSystemName) {
    byte[] bytes = generateInitVector(cryptoSystemName);
    bytes[0] = (byte) 0xFF;
    bytes[bytes.length - 1] = (byte) 0xFF;
    return new BigInteger(1, bytes);
  }

  public static byte[] extractKey(String cryptoSystemName, byte[] otherKey) {
    List<Integer> keyByteSizes = getKeySizes(cryptoSystemName);
    int idx = Collections.binarySearch(keyByteSizes, otherKey.length);
    if (idx >= 0) {
      return otherKey;
    }
    idx = -(idx + 1);
    if (idx == 0) {
      throw new IllegalArgumentException("otherKey is too small for the given cryptoSystem");
    }

    int length = keyByteSizes.get(idx - 1);
    byte[] key = Arrays.copyOf(otherKey, length);
    key[0] |= (byte) 0x80;
    return key;
  }

  public static SymmetricCryptoContext constructContext(String cryptoSystemName, byte[] key,
                                                        String cipherMode, String paddingMode,
                                                        byte[] initVector, BigInteger randomDelta) {
    key = extractKey(cryptoSystemName, key);
    paddingMode = paddingMode.replace(" ", "_");

    SymmetricCryptoSystem cryptoSystem = getCryptoSystem(cryptoSystemName, key);
    BlockCipherMode blockCipherMode = BlockCipherMode.valueOf(cipherMode);
    Padding padding = Padding.valueOf(paddingMode);
    Map<String, Object> extraConfig = Map.of(SymmetricCryptoContext.RD_PARAM, randomDelta);

    return new SymmetricCryptoContext(cryptoSystem, padding, blockCipherMode, initVector, extraConfig);
  }
}
