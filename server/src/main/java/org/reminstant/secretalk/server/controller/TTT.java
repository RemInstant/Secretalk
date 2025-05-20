package org.reminstant.secretalk.server.controller;

import io.nats.client.*;
import io.nats.client.api.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
public class TTT {

  public TTT() throws IOException, InterruptedException, JetStreamApiException {
    PullSubscribeOptions options = PullSubscribeOptions.builder()
        .durable("CONSUMER_1")
        .configuration(ConsumerConfiguration.builder()
            .ackPolicy(AckPolicy.Explicit)
            .deliverPolicy(DeliverPolicy.All)
            .ackWait(1)
            .build())
        .build();

    Connection nc = Nats.connect("nats://localhost:4223");
    JetStream js = nc.jetStream();

    // Настраиваем JetStream
    JetStreamManagement jsm = nc.jetStreamManagement();
    try {
      jsm.deleteStream("MESSENGER_STREAM");
//      jsm.deleteStream("ACK_STREAM");
//      jsm.deleteStream("DLQ_STREAM");
    } catch (JetStreamApiException e) {
      // Игнорируем если стрим не существует
    }

    jsm.addStream(StreamConfiguration.builder()
        .name("MESSENGER_STREAM")
        .subjects("message")
        .retentionPolicy(RetentionPolicy.WorkQueue)
        .storageType(StorageType.Memory)
        .build());
//    jsm.addStream(StreamConfiguration.builder()
//        .name("DLQ_STREAM")
//        .subjects("failed")
//        .retentionPolicy(RetentionPolicy.WorkQueue)
//        .storageType(StorageType.Memory)
//        .build());

    // Публикация сообщений
    js.publish("message", "Hello".getBytes());

    // Подписка с durable
    JetStreamSubscription sub = js.subscribe("message", options);

    // Чтение сообщения
    Message msg1 = sub.fetch(1, Duration.ofMillis(1000)).stream().findAny().orElse(null);
    System.out.println(msg1 == null ? "null" : new String(msg1.getData()));

    Thread.sleep(300);
    if (msg1 != null) {
//      msg1.ack();
    }
//    js.publish("ack", "Hello".getBytes());

    Message msg2 = sub.fetch(1, Duration.ofMillis(1000)).stream().findAny().orElse(null);
    System.out.println(msg2 == null ? "null" : new String(msg2.getData()));



//    // Отписка
//    sub.unsubscribe();
//
//    // Создание новой подписки
////    js = nc.jetStream();
//    JetStreamSubscription newSub = js.subscribe("ack", options);
//
//    // Чтение второго сообщения
//    Message msg2 = newSub.fetch(1, Duration.ofMillis(1000)).getFirst();
//    System.out.println(new String(msg2.getData()));
//    msg2.ack(); // Подтверждение
//
//
//    log.info("1: {}", msg1.metaData());
//    log.info("2: {}", msg2.metaData());

  }

}
