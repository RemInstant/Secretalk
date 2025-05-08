package org.reminstant.cryptomessengerserver.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Controller
public class DemoController {

  @GetMapping("get")
  ResponseEntity<String> getData() {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body("Hello");
  }

  @PostMapping("post")
  ResponseEntity<String> postData() {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body("Cool");
  }

  @GetMapping("long-polling")
  public DeferredResult<String> poll() {
    long timeOutInMilliSec = 100000L;
    String timeOutResp = "Time Out.";
    DeferredResult<String> deferredResult = new DeferredResult<>(timeOutInMilliSec, timeOutResp);
    CompletableFuture.runAsync(()-> {
      try {
        //Long polling task; if task is not completed within 100s, timeout response returned for this request
        TimeUnit.SECONDS.sleep(5);
        //set result after completing task to return response to client
        deferredResult.setResult("Ya long poll");
      } catch (Exception ex) {
      }
    });
    return deferredResult;
  }

  @GetMapping("sse")
  public SseEmitter sse() {
    SseEmitter emitter = new SseEmitter();
    ExecutorService sseMvcExecutor = Executors.newVirtualThreadPerTaskExecutor();

    sseMvcExecutor.execute(() -> {
      try {
        for (int i = 0; i < 10; i++) {
          SseEmitter.SseEventBuilder event = SseEmitter.event()
              .data("Ya Server sent event and menya poka ne nauchili russkomu")
              .name("sse");
          emitter.send(event);
          Thread.sleep(1000);
        }
        emitter.complete();
      } catch (Exception ex) {
        emitter.completeWithError(ex);
      }
    });


    return emitter;
  }

}
