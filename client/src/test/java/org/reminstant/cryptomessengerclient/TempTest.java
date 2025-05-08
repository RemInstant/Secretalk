package org.reminstant.cryptomessengerclient;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.Test;
import org.reminstant.concurrent.ConcurrentUtil;
import org.reminstant.cryptomessengerclient.model.SecretChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;

class TempTest {

  private static final Logger log = LoggerFactory.getLogger(TempTest.class);

  @Test
  void test() {

    log.info("{}", SecretChat.State.valueOf("PENDING"));

  }

}
