package org.reminstant.cryptomessengerserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.cryptomessengerserver.dto.http.*;
import org.reminstant.cryptomessengerserver.dto.nats.UserEvent;
import org.reminstant.cryptomessengerserver.service.NatsBrokerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
public class EventController {

  private final NatsBrokerService nats;

  EventController(NatsBrokerService natsBrokerService) {
    this.nats = natsBrokerService;
  }

  @GetMapping("${api.get-dh-params}")
  ResponseEntity<Map<String,String>> getDHParams(HttpServletRequest request, Principal principal) {
    logDebugHttpRequest(request, principal, null);
    // RFC 3526 4096-bit group
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(
            "generator",
            "2",
            "prime",
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
                "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
                "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
                "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
                "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
                "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64" +
                "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
                "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B" +
                "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
                "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
                "43DB5BFCE0FD108E4B82D120A92108011A723C12A787E6D7" +
                "88719A10BDBA5B2699C327186AF4E23C1A946834B6150BDA" +
                "2583E9CA2AD44CE8DBBBC2DB04DE8EF92E8EFC141FBECAA6" +
                "287C59474E6BC05D99B2964FA090C3A2233BA186515BE7ED" +
                "1F612970CEE2D7AFB81BDD762170481CD0069127D5B05AA9" +
                "93B4EA988D8FDDC186FFB7DC90A6C08F4DF435C934063199" +
                "FFFFFFFFFFFFFFFF"
        ));
  }

  @GetMapping("${api.get-event}")
  ResponseEntity<UserEventWrapper> getEvent(HttpServletRequest request,
                                            @RequestParam Long timeoutMillis,
                                            Principal principal) {
    logDebugHttpRequest(request, principal, null);
    if (timeoutMillis == null || timeoutMillis < 100) {
      timeoutMillis = 100L;
    }

    UserEvent rawEvent;
    try {
      rawEvent = nats.getEvent(principal.getName(), timeoutMillis);
    } catch (Exception ex) {
      log.error("Failed to get NATS event", ex);
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(new UserEventWrapper(rawEvent));
  }

  @PostMapping("${api.acknowledge-event}")
  ResponseEntity<Void> getEvent(HttpServletRequest request,
                                @RequestBody EventGetData data,
                                Principal principal) {
    logDebugHttpRequest(request, principal, data);
    try {
      boolean isOK = nats.acknowledgeEvent(principal.getName(), data.eventId());
      if (!isOK) {
        log.error("Tried to acknowledge non-existent event-message ({})", data.eventId());
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .build();
      }
    } catch (Exception ex) {
      log.error("Failed to get NATS event", ex);
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }



  @PostMapping("${api.request-chat-connection}")
  ResponseEntity<Void> requestChatConnection(HttpServletRequest request,
                                             @RequestBody ChatConnectionRequestData data,
                                             Principal principal) {
    logDebugHttpRequest(request, principal, data);
    try {
      nats.sendChatConnectionRequest(data.chatId(),
          principal.getName(), data.otherUsername(), data.publicKey());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex);
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @PostMapping("${api.accept-chat-connection}")
  ResponseEntity<Void> acceptChatConnection(HttpServletRequest request,
                                            @RequestBody ChatConnectionAcceptData data,
                                            Principal principal) {
    logDebugHttpRequest(request, principal, data);
    try {
      nats.sendChatConnectionAcceptance(data.chatId(),
          principal.getName(), data.otherUsername(), data.publicKey());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex);
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @PostMapping("${api.break-chat-connection}")
  ResponseEntity<Void> breakChatConnection(HttpServletRequest request,
                                           @RequestBody ChatCommonData data,
                                           Principal principal) {
    logDebugHttpRequest(request, principal, data);
    try {
      nats.sendChatConnectionBreak(data.chatId(), principal.getName(), data.otherUsername());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex);
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @PostMapping("${api.desert-chat}")
  ResponseEntity<Void> desertChat(HttpServletRequest request,
                                  @RequestBody ChatCommonData data,
                                  Principal principal) {
    logDebugHttpRequest(request, principal, data);
    try {
      nats.sendChatDeserting(data.chatId(), principal.getName(), data.otherUsername());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex);
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @PostMapping("${api.destroy-chat}")
  ResponseEntity<Void> destroyChat(HttpServletRequest request,
                                   @RequestBody ChatCommonData data,
                                   Principal principal) {
    logDebugHttpRequest(request, principal, data);
    try {
      nats.sendChatDestroying(data.chatId(), principal.getName(), data.otherUsername());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex);
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }



  private void logDebugHttpRequest(HttpServletRequest request, Principal principal, Object body) {
    String username = principal != null ? principal.getName() : "anonymous";
    String method = "%4s".formatted(request.getMethod());
    log.debug("User {}: {} {} | body: {}", username, method, request.getRequestURL(), body);
  }
}