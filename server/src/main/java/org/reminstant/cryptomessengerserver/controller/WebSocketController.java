package org.reminstant.cryptomessengerserver.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.concurrent.CompletableFuture;

//@Configuration
//@EnableWebSocket
//@EnableWebMvc
public class WebSocketController implements WebSocketConfigurer {
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(new MyHandler(), "/websocket")
        .setAllowedOrigins("*");
  }
}

class MyHandler extends AbstractWebSocketHandler {
  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
    CompletableFuture.runAsync(() -> {
      try {
        for (int i = 0; i < 10; i++) {
          session.sendMessage(new TextMessage("Я веб-сокет"));
          Thread.sleep(1000);
        }
        session.close();
      } catch (Exception ex) {
//        session.close(CloseStatus.SERVER_ERROR);
      }
    });
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    session.sendMessage(new TextMessage("echo: " + message.getPayload()));
  }
}
