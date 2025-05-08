package org.reminstant.cryptomessengerclient;

import javafx.application.Application;
import org.reminstant.cryptomessengerclient.application.ClientApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringBootWrapperApplication {

  public static void main(String[] args) {
    Application.launch(ClientApplication.class, args);
  }
}
