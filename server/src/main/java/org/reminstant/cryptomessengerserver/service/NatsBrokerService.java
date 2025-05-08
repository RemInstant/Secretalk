package org.reminstant.cryptomessengerserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.*;
import io.nats.client.api.*;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.cryptomessengerserver.dto.nats.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NatsBrokerService implements DisposableBean {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final String subjectPrefix;

  private final Connection natsConnection;
  private final JetStreamManagement jetStreamManagement;
  private final JetStream jetStream;

  private final Map<String, JetStreamSubscription> subscriptions;
  private final Map<String, Map<String, Message>> messagesToAcknowledge;

  public NatsBrokerService(@Value("${nats.server.url}") String url,
                           @Value("${nats.streams.event.name}") String streamName,
                           @Value("${nats.streams.event.subject-prefix}") String subjectPrefix)
      throws IOException, InterruptedException, JetStreamApiException {
    this.subjectPrefix = subjectPrefix;
    subscriptions = new ConcurrentHashMap<>();
    messagesToAcknowledge = new ConcurrentHashMap<>();

    natsConnection = Nats.connect(url);

    jetStreamManagement = natsConnection.jetStreamManagement();
    jetStreamManagement.addStream(StreamConfiguration.builder()
        .name(streamName)
        .subjects("%s.>".formatted(subjectPrefix))
        .retentionPolicy(RetentionPolicy.WorkQueue)
        .storageType(StorageType.Memory) // TODO: change to file
        .build());

    jetStream = natsConnection.jetStream();
  }

  @Override
  public void destroy() throws Exception {
    natsConnection.close();
  }

  public UserEvent getEvent(String username, long timeoutMillis)
      throws JetStreamApiException, IOException {
    Objects.requireNonNull(username, "username cannot be null");

    JetStreamSubscription sub = getSubscription(username);
    Message msg = sub.fetch(1, timeoutMillis).stream().findFirst().orElse(null);

    if (msg == null) {
      return UserEvent.getVoidEvent();
    }

    String eventType = msg.getHeaders().get("Event-Type").stream().findFirst().orElse(null);
    if (eventType == null) {
      log.error("Found NATS event-message without event type.{} Message metadata:{}{}",
          System.lineSeparator(), System.lineSeparator(), msg.metaData());
      return UserEvent.getVoidEvent();
    }

    Map<String, String> data = objectMapper.readValue(msg.getData(), new TypeReference<>() {});
    UserEvent event = UserEvent.getEvent(eventType, data);

    saveMessageToAcknowledge(username, event.getId(), msg);
    return event;
  }

  public boolean acknowledgeEvent(String username, String eventId) {
    Objects.requireNonNull(username, "username cannot be null");
    Objects.requireNonNull(eventId, "eventId cannot be null");
    return eraseMessageToAcknowledge(username, eventId);
  }



  public void sendChatConnectionRequest(String chatId,
                                        String username,
                                        String otherUsername,
                                        String publicKey)
      throws JetStreamApiException, IOException {
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(username, "username cannot be null");
    Objects.requireNonNull(otherUsername, "otherUsername cannot be null");
    Objects.requireNonNull(publicKey, "publicKey cannot be null");

    String subject = getUserEventSubject(otherUsername);
    Headers headers = new Headers()
        .put("Event-Type", ChatConnectionRequestEvent.EVENT_NAME);

    try {
      String json = objectMapper
          .writeValueAsString(new ChatConnectionRequestEvent(chatId, username, publicKey));
      jetStream.publish(subject, headers, json.getBytes());
    } catch (JsonProcessingException ex) {
      log.error("Failed to jsonify ChatConnectionRequestEvent");
      throw new RuntimeException(ex);
    }
  }

  public void sendChatConnectionAcceptance(String chatId,
                                           String username,
                                           String otherUsername,
                                           String publicKey)
      throws JetStreamApiException, IOException {
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(username, "username cannot be null");
    Objects.requireNonNull(otherUsername, "otherUsername cannot be null");
    Objects.requireNonNull(publicKey, "publicKey cannot be null");

    String subject = getUserEventSubject(otherUsername);
    Headers headers = new Headers()
        .put("Event-Type", ChatConnectionAcceptEvent.EVENT_NAME);

    try {
      String json = objectMapper
          .writeValueAsString(new ChatConnectionAcceptEvent(chatId, username, publicKey));
      jetStream.publish(subject, headers, json.getBytes());
    } catch (JsonProcessingException ex) {
      log.error("Failed to jsonify ChatConnectionAcceptEvent");
      throw new RuntimeException(ex);
    }
  }

  public void sendChatConnectionBreak(String chatId,
                                      String username,
                                      String otherUsername)
      throws JetStreamApiException, IOException {
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(username, "username cannot be null");
    Objects.requireNonNull(otherUsername, "otherUsername cannot be null");

    String subject = getUserEventSubject(otherUsername);
    Headers headers = new Headers()
        .put("Event-Type", ChatConnectionBreakEvent.EVENT_NAME);

    try {
      String json = objectMapper
          .writeValueAsString(new ChatConnectionBreakEvent(chatId, username));
      jetStream.publish(subject, headers, json.getBytes());
    } catch (JsonProcessingException ex) {
      log.error("Failed to jsonify ChatConnectionBreakEvent");
      throw new RuntimeException(ex);
    }
  }

  public void sendChatDeserting(String chatId,
                                String username,
                                String otherUsername)
      throws JetStreamApiException, IOException {
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(username, "username cannot be null");
    Objects.requireNonNull(otherUsername, "otherUsername cannot be null");

    String subject = getUserEventSubject(otherUsername);
    Headers headers = new Headers()
        .put("Event-Type", ChatDesertEvent.EVENT_NAME);

    try {
      String json = objectMapper
          .writeValueAsString(new ChatDesertEvent(chatId, username));
      jetStream.publish(subject, headers, json.getBytes());
    } catch (JsonProcessingException ex) {
      log.error("Failed to jsonify ChatDesertEvent");
      throw new RuntimeException(ex);
    }
  }

  public void sendChatDestroying(String chatId,
                                 String username,
                                 String otherUsername)
      throws JetStreamApiException, IOException {
    Objects.requireNonNull(chatId, "chatId cannot be null");
    Objects.requireNonNull(username, "username cannot be null");
    Objects.requireNonNull(otherUsername, "otherUsername cannot be null");

    String subject = getUserEventSubject(otherUsername);
    Headers headers = new Headers()
        .put("Event-Type", ChatDestroyEvent.EVENT_NAME);

    try {
      String json = objectMapper
          .writeValueAsString(new ChatDestroyEvent(chatId, username));
      jetStream.publish(subject, headers, json.getBytes());
    } catch (JsonProcessingException ex) {
      log.error("Failed to jsonify ChatDestroyEvent");
      throw new RuntimeException(ex);
    }
  }


  private JetStreamSubscription getSubscription(String username)
      throws JetStreamApiException, IOException {
    // TODO: cache cleaning
    JetStreamSubscription sub = subscriptions.getOrDefault(username, null);
    if (sub == null) {
      String subject = "%s.user-%s".formatted(subjectPrefix, username);
      PullSubscribeOptions options = PullSubscribeOptions.builder()
          .durable("CONSUMER_%s".formatted(username))
          .configuration(ConsumerConfiguration.builder()
              .ackPolicy(AckPolicy.Explicit)
              .deliverPolicy(DeliverPolicy.All)
              .ackWait(1)
              .build())
          .build();
      sub = jetStream.subscribe(subject, options);
      subscriptions.put(username, sub);
    }
    return sub;
  }

  private String getUserEventSubject(String otherUsername) {
    return "%s.user-%s".formatted(subjectPrefix, otherUsername);
  }

  private void saveMessageToAcknowledge(String username, String eventId, Message msg) {
    messagesToAcknowledge
        .computeIfAbsent(username, _ -> new ConcurrentHashMap<>())
        .put(eventId, msg);
  }

  private boolean eraseMessageToAcknowledge(String username, String eventId) {
    Message msg = messagesToAcknowledge
        .computeIfAbsent(username, _ -> new ConcurrentHashMap<>())
        .getOrDefault(eventId, null);

    if (msg == null) {
      return false;
    }

    msg.ack();
    return true;
  }
}