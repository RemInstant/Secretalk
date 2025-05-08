package org.reminstant.cryptomessengerserver.controller;


import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Duration;
import java.util.*;

@Slf4j
@Controller
public class KafkaTestController {

  private final KafkaProducer<String, String> producer;
  private final KafkaConsumer<String, String> consumer;

  private final Map<String, Long> offsets;


  public KafkaTestController() {
    Properties producerProp = new Properties();
    producerProp.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    producerProp.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProp.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    this.producer = new KafkaProducer<>(producerProp);

    Properties consumerProps = new Properties();
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
//    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "controller-consumer-group"); // Уникальный group.id для клиента
//    consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // Отключаем авто-коммит!
//    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Чтение с начала при первом запросе
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    this.consumer = new KafkaConsumer<>(consumerProps);

    consumer.assign(List.of(new TopicPartition("testTopic", 0)));
//    consumer.subscribe(Collections.singletonList("testTopic"));

    offsets = new HashMap<>();
  }


  @PostMapping("/kafka/send")
  public ResponseEntity<Void> send(@RequestBody Map<String, String> data) {
    String receiverId = data.getOrDefault("receiverID", null);
    String message = data.getOrDefault("message", null);

    if (receiverId == null || message == null) {
      return ResponseEntity.badRequest().build();
    }

    // Ключ сообщения - receiver_id
    ProducerRecord<String, String> rec =
        new ProducerRecord<>("testTopic", receiverId, message);

    producer.send(rec, (metadata, exception) -> {
      if (exception != null) {
        log.error("Error sending message: " + exception.getMessage());
      } else {
        log.info("Sent message to {} with key {}: {}", metadata.topic(), receiverId, message);
      }
    });

    return ResponseEntity.ok().build();
  }

  @PostMapping("/kafka/read")
  public ResponseEntity<Map<String,String>> read(@RequestBody Map<String, String> data) {
    String receiverId = data.getOrDefault("receiverID", null);

    if (receiverId == null) {
      return ResponseEntity.badRequest().build();
    }

    Long offset = offsets.getOrDefault(receiverId, 0L);
    consumer.seek(new TopicPartition("testTopic", 0), offset);

    // Чтение одного сообщения
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
    if (records.isEmpty()) {
      return ResponseEntity.ok()
          .body(Map.of("message", "null"));
    }

    ConsumerRecord<String, String> rec = records.iterator().next();

    return ResponseEntity.ok()
        .body(Map.of("message", rec.value(), "offset", String.valueOf(rec.offset())));
  }

  @PostMapping("/kafka/ack")
  public ResponseEntity<Void> ack(@RequestBody Map<String, String> data) {

    String receiverId = data.getOrDefault("receiverID", null);
    String offset = data.getOrDefault("offset", null);

    if (receiverId == null || offset == null) {
      return ResponseEntity.badRequest().build();
    }

//    consumer.commitSync(Collections.singletonMap(
//        new TopicPartition("testTopic", 0),
//        new OffsetAndMetadata(Long.parseLong(offset) + 1) // <- Коммитим следующий offset
//    ));

    offsets.put(receiverId, Long.parseLong(offset) + 1);

    return ResponseEntity.ok().build();
  }


}
