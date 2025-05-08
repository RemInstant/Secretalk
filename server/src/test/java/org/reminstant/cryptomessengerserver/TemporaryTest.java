package org.reminstant.cryptomessengerserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

class TemporaryTest {

  private static final Logger log = LoggerFactory.getLogger(TemporaryTest.class);

  @Test
  void test() throws NoSuchAlgorithmException {
    String str = Encoders.BASE64.encode("13874173513751037518571314134815719735".getBytes());
    byte[] keyBytes = Decoders.BASE64.decode(str);
    SecretKey k = Keys.hmacShaKeyFor(keyBytes);
    log.info("{}", k.equals(Keys.hmacShaKeyFor(keyBytes)));
  }



}
