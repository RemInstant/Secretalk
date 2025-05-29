package org.reminstant.secretalk.client.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.secretalk.client.dto.*;
import org.reminstant.secretalk.client.exception.*;
import org.reminstant.secretalk.client.model.Chat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

  private static final Duration STANDARD_TIMEOUT = Duration.ofSeconds(10);
  
  private static final HttpClient httpClient = HttpClient.newHttpClient();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Getter
  private String username = null;
  private String jwtToken = null;


  public JwtResponse processLogin(String login, String password)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    String data = login + ":" + password;
    String base64data = Base64.getEncoder().encodeToString(data.getBytes());

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/auth/login"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BASIC + base64data)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build(), null, JwtResponse.class);
  }

  public NoPayloadResponse processRegister(String login, String password) throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "username", login,
        "password", password);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/auth/register"))
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .timeout(STANDARD_TIMEOUT)
        .build(), data, NoPayloadResponse.class);
  }

  public void saveCredentials(String username, String jwtToken) {
    this.username = username;
    this.jwtToken = jwtToken;
  }

  public void eraseCredentials() {
    this.username = null;
    this.jwtToken = null;
  }



  public DHResponse getDHParams()
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/get-dh-params"))
        .GET()
        .build(), null, DHResponse.class);
  }



  public UserEventWrapperResponse getEvent(long timeoutMillis)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/get-event?timeoutMillis=%d"
            .formatted(timeoutMillis)))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .GET()
        .build(), null, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse acknowledgeEvent(String eventId)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of("eventId", eventId);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/ack-event"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, NoPayloadResponse.class);
  }



  public NoPayloadResponse desertChat(String chatId, String otherUsername)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/desert-chat"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse destroyChat(String chatId, String otherUsername)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/destroy-chat"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse requestChatConnection(String chatId, String otherUsername,
                                                 Chat.Configuration config, String publicKey)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername,
        "chatConfiguration", config,
        "publicKey", publicKey);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/request-chat-connection"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .timeout(STANDARD_TIMEOUT)
        .build(), data, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse acceptChatConnection(String chatId, String otherUsername, String publicKey)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername,
        "publicKey", publicKey);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/accept-chat-connection"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse breakChatConnection(String chatId, String otherUsername)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "chatId", chatId,
        "otherUsername", otherUsername);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/break-chat-connection"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse sendChatMessage(String messageId, String chatId, String otherUsername,
                                           byte[] messageData)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "messageId", messageId,
        "chatId", chatId,
        "otherUsername", otherUsername,
        "messageData", messageData,
        "isImage", false);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/send-chat-message"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse sendChatMessage(String messageId, String chatId, String otherUsername,
                                           byte[] messageData, String attachedFileName, boolean isImage)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "messageId", messageId,
        "chatId", chatId,
        "otherUsername", otherUsername,
        "messageData", messageData,
        "attachedFileName", attachedFileName,
        "isImage", isImage);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/send-chat-message"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse sendImage(String messageId, String chatId, String otherUsername,
                                     String fileName, byte[] fileData)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "messageId", messageId,
        "chatId", chatId,
        "otherUsername", otherUsername,
        "fileName", fileName,
        "imageData", fileData);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/send-image"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse sendFilePart(String messageId, String chatId, String otherUsername,
                                        int partNumber, int partCount, byte[] fileData)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "messageId", messageId,
        "chatId", chatId,
        "otherUsername", otherUsername,
        "partNumber", partNumber,
        "partCount", partCount,
        "fileData", fileData);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/send-file-part"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, UserEventWrapperResponse.class);
  }

  public NoPayloadResponse requestMessageFile(String messageId, String chatId, String otherUsername)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    Map<String, Object> data = Map.of(
        "messageId", messageId,
        "chatId", chatId,
        "otherUsername", otherUsername);
    String json = jsonifyMap(data);

    return sendRequest(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/chat/request-message-file"))
        .header(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_BEARER + jwtToken)
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_HEADER_APPLICATION_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build(), data, UserEventWrapperResponse.class);
  }



  private String jsonifyMap(Map<String, ?> data) {
    try {
      return objectMapper.writeValueAsString(data);
    } catch (JsonProcessingException ex) {
      throw new ServerRequestPreparationException(ex);
    }
  }
  
  private <T> T sendRequest(HttpRequest request, Map<String, Object> data, Class<T> c)
      throws ServerConnectionException, ServerResponseException, InterruptedException {
    HttpResponse<String> response;
    try {
      log.debug("send {} {} | body: {}", request.method(), request.uri(), data);
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      String bodyString = null;
      if (response.body() != null) {
        bodyString = response.body();
        bodyString = bodyString.substring(0, Math.min(512, bodyString.length())); //TODO: move constant to config
      }
      log.debug("get {} {} - code {} | body: {}", request.method(), request.uri(),
          response.statusCode(), bodyString);
    } catch (IOException ex) {
      throw new ServerConnectionException(ex);
    } catch (InterruptedException ex) {
      log.debug("{} {} - cancelled", request.method(), request.uri());
      throw ex;
    }

    if (response.body() == null || response.body().isEmpty()) {
      throw new UnexpectedServerResponseException(request.uri(), "no body");
    }

    try {
      return objectMapper.readValue(response.body(), c);
    } catch (JsonProcessingException ex) {
      log.error("Failed to parse server response", ex);
      throw new UnparsableServerResponseException(request.uri(), "Invalid body: " + response.body(), ex);
    }
  }
}