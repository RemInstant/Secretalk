package org.reminstant.secretalk.client;

import javafx.application.Application;
import org.reminstant.secretalk.client.application.ClientApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringBootWrapperApplication {

  public static void main(String[] args) {
    Application.launch(ClientApplication.class, args);
  }
}
