package org.reminstant.cryptomessengerclient.component;


import java.math.BigInteger;
import java.util.Random;

public class DiffieHellmanGenerator {

  private final Random random;
  private final BigInteger prime;
  private final BigInteger generator;

  public DiffieHellmanGenerator(BigInteger prime, BigInteger generator) {
    this.random = new Random();
    this.prime = prime;
    this.generator = generator;
  }

  public DiffieHellmanGenerator(String prime, String generator) {
    this(new BigInteger(prime, 16), new BigInteger(generator, 16));
  }

  public BigInteger generatePrivateKey(int bitLength) {
    return new BigInteger(bitLength, random).setBit(bitLength - 1);
  }

  public BigInteger generatePublicKey(BigInteger privateKey) {
    return generator.modPow(privateKey, prime);
  }

  public BigInteger generateSessionKey(BigInteger privateKey, BigInteger otherPublicKey) {
    return otherPublicKey.modPow(privateKey, prime);
  }

}
