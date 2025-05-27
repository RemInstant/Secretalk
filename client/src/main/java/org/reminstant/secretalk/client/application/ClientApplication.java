package org.reminstant.secretalk.client.application;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.reminstant.secretalk.client.SpringBootWrapperApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClientApplication extends Application {

  private static final Logger log = LoggerFactory.getLogger(ClientApplication.class);

  private ConfigurableApplicationContext applicationContext;

  @Override
  public void init() {
    log.info("INIT");
    String[] args = getParameters().getRaw().toArray(new String[0]);

    this.applicationContext = new SpringApplicationBuilder()
        .sources(SpringBootWrapperApplication.class)
//        .initializers((ConfigurableApplicationContext context) ->
//            context.getBeanFactory().registerSingleton("hostServices", getHostServices()))
        .run(args);
  }

  @Override
  public void start(Stage stage) {
    log.info("START");
    ApplicationStateManager stateManager = applicationContext.getBean(ApplicationStateManager.class);
    stateManager.init(stage);
  }

  @Override
  public void stop() {
    this.applicationContext.close();
    Platform.exit();
  }

  @Bean
  HostServices hostServices() {
    return getHostServices();
  }
}
