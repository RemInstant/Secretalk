package org.reminstant.cryptomessengerclient;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class TempTest {

  private static final Logger log = LoggerFactory.getLogger(TempTest.class);

  @Test
  void test() throws IOException { // NOSONAR

    Files.createFile(Path.of("/home/remi/Code/abc"));
    Files.createFile(Path.of("/home/remi/Code/abc"));
    Files.createFile(Path.of("/home/remi/Code/abc"));

//    try (FileChannel fileChannel = FileChannel.open(Path.of("/home/remi/Code/abc"),
//        StandardOpenOption.CREATE)) {
//
//    }


  }

}
