package org.reminstant.cryptography;

import java.util.ArrayList;
import java.util.Collection;

public class GaloisField256 {

  private static final int CARDINALITY = 256;

  private GaloisField256() {

  }

  public static boolean isPolynomialIrreducible(byte polynomial) {
    for (byte fieldElement = 2; fieldElement < 32; ++fieldElement) {
      if (isPolynomial8Divides(polynomial, fieldElement)) {
        return false;
      }
    }
    return true;
  }

  public static Collection<Byte> getIrreduciblePolynomials8() {
    Collection<Byte> polynomials = new ArrayList<>();
    for (byte p = 1; p != 0; ++p) {
      if (isPolynomialIrreducible(p)) {
        polynomials.add(p);
      }
    }
    return polynomials;
  }

  public static byte sum(byte a, byte b) {
    return (byte) (a ^ b);
  }

  public static byte product(byte a, byte b, byte polynomial) {
    if (!isPolynomialIrreducible(polynomial)) {
      throw new ArithmeticException("Polynomial is reducible in GF(256)");
    }
    int l = Byte.toUnsignedInt(a);
    int r = Byte.toUnsignedInt(b);
    byte res = 0;
    while (r != 0) {
      if ((r & 1) != 0) {
        res ^= (byte) l;
      }
      l <<= 1;
      r >>>= 1;
      if ((l & 0x100) != 0) {
        l ^= polynomial;
      }
    }
    return res;
  }

  private static boolean isPolynomial8Divides(byte dividend, byte divisor) {
    if (divisor == 0) {
      throw new ArithmeticException("division by zero");
    }
    int dividendDegree = 8;
    int divisorDegree = Integer.SIZE - Integer.numberOfLeadingZeros(divisor) - 1;

    do {
      dividend ^= (byte) (divisor << (dividendDegree - divisorDegree));
      dividendDegree = Integer.SIZE - Integer.numberOfLeadingZeros(Byte.toUnsignedInt(dividend)) - 1;
    } while (dividendDegree >= divisorDegree);

    return dividend == 0;
  }
}
