package org.reminstant.cryptomessengerserver.controller;


import io.nats.client.*;
import io.nats.client.api.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
//@Controller
public class NatsTestController {

//  private final KafkaProducer<String, String> producer;
//  private final KafkaConsumer<String, String> consumer;
//
//  private final Map<String, Long> offsets;

  private final Connection nc;
  private final JetStreamManagement jsm;
  private final JetStream js;

  public NatsTestController() throws IOException, InterruptedException, JetStreamApiException {
    // Подключаемся к NATS
    nc = Nats.connect("localhost:4223");

    // Настраиваем JetStream
    jsm = nc.jetStreamManagement();
    try {
      jsm.deleteStream("MESSENGER_STREAM");
    } catch (JetStreamApiException e) {
      // Игнорируем если стрим не существует
    }

    StreamConfiguration sc = StreamConfiguration.builder()
        .name("MESSENGER_STREAM")
        .subjects("MESSENGER.>")
        .retentionPolicy(RetentionPolicy.WorkQueue)
        .storageType(StorageType.Memory)
        .build();
    jsm.addStream(sc);

    js = nc.jetStream();
  }



  @PostMapping("/nats/send")
  public ResponseEntity<Void> send(@RequestBody Map<String, String> data) throws JetStreamApiException, IOException {
    String receiverId = data.getOrDefault("receiverID", null);
    String message = data.getOrDefault("message", null);

    if (receiverId == null || message == null) {
      return ResponseEntity.badRequest().build();
    }

    String subject = "MESSENGER.MESSAGE." + receiverId;
    js.publish(subject, message.getBytes(StandardCharsets.UTF_8));

    return ResponseEntity.ok().build();
  }

  @PostMapping("/nats/read")
  public ResponseEntity<Map<String,String>> read(@RequestBody Map<String, String> data) throws JetStreamApiException, IOException, InterruptedException {
    String receiverId = data.getOrDefault("receiverID", null);

    if (receiverId == null) {
      return ResponseEntity.badRequest().build();
    }

    String subject = "MESSENGER.MESSAGE." + receiverId;

    PullSubscribeOptions options = PullSubscribeOptions.builder()
        .durable("CONSUMER_" + receiverId)
        .configuration(ConsumerConfiguration.builder()
            .ackPolicy(AckPolicy.Explicit)
            .deliverPolicy(DeliverPolicy.All)
            .build())
        .build();

    JetStreamSubscription sub = js.subscribe(subject, options);
    StringBuilder messages = new StringBuilder();

    Message msg = sub.fetch(1, Duration.ofMillis(2500)).stream().findFirst().orElse(null);
//
//    while (msg != null) {
//      String messageData = new String(msg.getData(), StandardCharsets.UTF_8);
//      messages.append(messageData).append("|");
////      msg.ack();
//      break;
////      msg = sub.nextMessage(Duration.ofMillis(100));
//    }
////
//    sub.unsubscribe();

//    return ResponseEntity.ok()
//        .body(Map.of("message", messages.toString().trim()));

    if (msg == null) {
      sub.unsubscribe();
      return ResponseEntity.ok()
          .body(Map.of("message", "null"));
    }
//
//    msg.ack();
    sub.unsubscribe();

    long sequence = msg.metaData().streamSequence();

    return ResponseEntity.ok()
        .body(Map.of("message", msg.toString(), "sequence", String.valueOf(sequence)));
  }

  @PostMapping("/kafka/ack")
  public ResponseEntity<Void> ack(@RequestBody Map<String, String> data) throws JetStreamApiException, IOException {

    String receiverId = data.getOrDefault("receiverID", null);
    String sequence = data.getOrDefault("sequence", null);

    if (receiverId == null || sequence == null) {
      return ResponseEntity.badRequest().build();
    }

    // Получаем ConsumerContext по subject (нужно знать consumer name)
    String consumerName = "CONSUMER_" + receiverId;

    ConsumerContext consumerContext = js.getConsumerContext("MESSENGER_STREAM", consumerName);



//    consumer.commitSync(Collections.singletonMap(
//        new TopicPartition("testTopic", 0),
//        new OffsetAndMetadata(Long.parseLong(offset) + 1) // <- Коммитим следующий offset
//    ));

//    offsets.put(receiverId, Long.parseLong(offset) + 1);

    return ResponseEntity.ok().build();
  }


}
