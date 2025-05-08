package org.reminstant.cryptomessengerclient.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.cryptomessengerclient.dto.*;
import org.reminstant.cryptomessengerclient.exception.InvalidServerAnswer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class ServerClient {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String AUTHORIZATION_HEADER_BASIC = "Basic ";
  private static final String AUTHORIZATION_HEADER_BEARER = "Bearer ";

  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String CONTENT_TYPE_HEADER_APPLICATION_JSON = "application/json";
  
  private static final HttpClient httpClient = HttpClient.newHttpClient();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Getter
  private String username = null;
  private String jwtToken = null;


  public JwtResponse processLogin(String login, String password) throws IOException, InvalidServerAnswer {
    String data = login + ":" + password;
    String base64data = Base64.getEncoder().encodeToString(data.getBytes());

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/auth/login"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BASIC + base64data)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build(), JwtResponse.class);
  }

  public NoPayloadResponse processRegister(String login, String password) throws IOException, InvalidServerAnswer {
    Map<String, String> data = Map.of("username", login, "password", password);
    String json = objectMapper.writeValueAsString(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/auth/register"))
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), NoPayloadResponse.class);
  }

  public void saveCredentials(String username, String jwtToken) {
    this.username = username;
    this.jwtToken = jwtToken;
  }

  public void eraseCredentials() {
    this.username = null;
    this.jwtToken = null;
  }



  public DHResponse getDHParams() throws IOException, InvalidServerAnswer {
    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/get-dh-params"))
        .GET()
        .build(), DHResponse.class);
  }



  public UserEventWrapperResponse getEvent(long timeoutMillis) throws IOException, InvalidServerAnswer {
    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/get-event?timeoutMillis=%d"
            .formatted(timeoutMillis)))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .GET()
        .build(), UserEventWrapperResponse.class);
  }

  public NoPayloadResponse acknowledgeEvent(String eventId) throws IOException, InvalidServerAnswer {
    String json = objectMapper.writeValueAsString(Map.of("eventId", eventId));

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/ack-event"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), NoPayloadResponse.class);
  }



  public NoPayloadResponse desertChat(String chatId, String otherUsername)
      throws IOException, InvalidServerAnswer {
    String json = objectMapper.writeValueAsString(Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername));

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/desert-chat"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), UserEventWrapperResponse.class);
  }

  public NoPayloadResponse destroyChat(String chatId, String otherUsername)
      throws IOException, InvalidServerAnswer {
    String json = objectMapper.writeValueAsString(Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername));

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/destroy-chat"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), UserEventWrapperResponse.class);
  }

  public NoPayloadResponse requestChatConnection(String chatId, String otherUsername, String publicKey)
      throws IOException, InvalidServerAnswer {
    String json = objectMapper.writeValueAsString(Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername,
        "publicKey", publicKey));

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/request-chat-connection"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .timeout(Duration.ofMillis(1000))
        .build(), UserEventWrapperResponse.class);
  }

  public NoPayloadResponse acceptChatConnection(String chatId, String otherUsername, String publicKey)
      throws IOException, InvalidServerAnswer {
    String json = objectMapper.writeValueAsString(Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername,
        "publicKey", publicKey));

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/accept-chat-connection"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), UserEventWrapperResponse.class);
  }

  public NoPayloadResponse breakChatConnection(String chatId, String otherUsername)
      throws IOException, InvalidServerAnswer {
    String json = objectMapper.writeValueAsString(Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername));

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/break-chat-connection"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), UserEventWrapperResponse.class);
  }



  private <T> T sendRequest(HttpRequest request, Class<T> c) throws IOException, InvalidServerAnswer {
    HttpResponse<String> response = null;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      log.debug("{} {} - code {} | body: {}", request.method(), request.uri(),
          response.statusCode(), response.body());
    } catch (InterruptedException ex) {
      log.error("Unexpected interruption", ex);
      Thread.currentThread().interrupt();
    }

    if (response == null || response.body() == null || response.body().isEmpty()) {
      throw new InvalidServerAnswer(request.uri(), "no body");
    }

    try {
      return objectMapper.readValue(response.body(), c);
    } catch (JsonProcessingException ex) {
      log.error("Failed to parse server response", ex);
      throw new InvalidServerAnswer(request.uri(), "Invalid body: " + response.body(), ex);
    }
  }

//  private <T> T getDefaultResponseInstance(Class<T> c) {
//    try {
//      return c.getConstructor().newInstance();
//    } catch (ReflectiveOperationException ex) {
//      log.error("Cannot construct default response instance", ex);
//      return null;
//    }
//  }
}