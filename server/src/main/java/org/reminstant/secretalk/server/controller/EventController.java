package org.reminstant.secretalk.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.secretalk.server.dto.http.*;
import org.reminstant.secretalk.server.dto.nats.UserEvent;
import org.reminstant.secretalk.server.exception.LocalFileStorageException;
import org.reminstant.secretalk.server.repository.LocalFileStorage;
import org.reminstant.secretalk.server.service.AppUserService;
import org.reminstant.secretalk.server.service.NatsBrokerService;
import org.reminstant.secretalk.server.util.InternalStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@RestController
public class EventController {
  
  private static final int FILE_BLOCK_BYTE_SIZE = 128 * (1 << 10);

  private static final ResponseEntity<StatusWrapper> internalErrorResponse = ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .contentType(MediaType.APPLICATION_JSON)
      .build();

  private static final ResponseEntity<StatusWrapper> nonExistentUserResponse = ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(new StatusWrapper(InternalStatus.NON_EXISTENT_USER));

  private static final ResponseEntity<StatusWrapper> selfRequestResponse = ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(new StatusWrapper(InternalStatus.SELF_REQUEST));

  private static final ResponseEntity<StatusWrapper> tooMuchDataResponse = ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(new StatusWrapper(InternalStatus.TO_MUCH_DATA));

  private final NatsBrokerService nats;
  private final AppUserService userService;
  private final LocalFileStorage fileStorage;

  @Value("${chat.message.text-max-byte-length}")
  private int textMaxByteLength;
  @Value("${chat.message.file-part-byte-length}")
  private int filePartByteLength;

  EventController(NatsBrokerService natsBrokerService,
                  AppUserService userService,
                  LocalFileStorage fileStorage) {
    this.nats = natsBrokerService;
    this.userService = userService;
    this.fileStorage = fileStorage;
  }

  @GetMapping("${api.get-dh-params}")
  ResponseEntity<Map<String,String>> getDHParams(HttpServletRequest request, Principal principal) {
    logDebugHttpRequest(request, principal, null);
    // RFC 3526 4096-bit group TODO: put into file
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
                                            @RequestParam(required = false) Long timeoutMillis,
                                            Principal principal) {
    logDebugHttpRequest(request, principal, null);
    if (timeoutMillis == null || timeoutMillis < 300) {
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
//        .contentType(MediaType.APPLICATION_JSON)
        .body(new UserEventWrapper(rawEvent));
  }

  @PostMapping("${api.acknowledge-event}")
  ResponseEntity<Void> ackEvent(HttpServletRequest request,
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
  ResponseEntity<StatusWrapper> requestChatConnection(HttpServletRequest request,
                                                      @RequestBody ChatConnectionRequestData data,
                                                      Principal principal) {
    logDebugHttpRequest(request, principal, data);
    if (!userService.isUserExistent(data.otherUsername())) {
      return nonExistentUserResponse;
    }
    if (principal.getName().equals(data.otherUsername())) {
      return selfRequestResponse;
    }

    try {
      nats.sendChatConnectionRequest(data.chatId(), principal.getName(),
          data.otherUsername(), data.chatConfiguration(), data.publicKey());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex); // NOSONAR
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
  ResponseEntity<StatusWrapper> acceptChatConnection(HttpServletRequest request,
                                                    @RequestBody ChatConnectionAcceptData data,
                                            Principal principal) {
    logDebugHttpRequest(request, principal, data);
    if (!userService.isUserExistent(data.otherUsername())) {
      return nonExistentUserResponse;
    }
    if (principal.getName().equals(data.otherUsername())) {
      return selfRequestResponse;
    }

    try {
      nats.sendChatConnectionAcceptance(data.chatId(),
          principal.getName(), data.otherUsername(), data.publicKey());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex); // NOSONAR
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
  ResponseEntity<StatusWrapper> breakChatConnection(HttpServletRequest request,
                                                    @RequestBody ChatCommonData data,
                                                    Principal principal) {
    logDebugHttpRequest(request, principal, data);
    if (!userService.isUserExistent(data.otherUsername())) {
      return nonExistentUserResponse;
    }
    if (principal.getName().equals(data.otherUsername())) {
      return selfRequestResponse;
    }

    try {
      nats.sendChatConnectionBreak(data.chatId(), principal.getName(), data.otherUsername());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex); // NOSONAR
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
  ResponseEntity<StatusWrapper> desertChat(HttpServletRequest request,
                                           @RequestBody ChatCommonData data,
                                           Principal principal) {
    logDebugHttpRequest(request, principal, data);
    if (!userService.isUserExistent(data.otherUsername())) {
      return nonExistentUserResponse;
    }
    if (principal.getName().equals(data.otherUsername())) {
      return selfRequestResponse;
    }

    try {
      nats.sendChatDeserting(data.chatId(), principal.getName(), data.otherUsername());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex); // NOSONAR
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
  ResponseEntity<StatusWrapper> destroyChat(HttpServletRequest request,
                                            @RequestBody ChatCommonData data,
                                            Principal principal) {
    logDebugHttpRequest(request, principal, data);
    if (!userService.isUserExistent(data.otherUsername())) {
      return nonExistentUserResponse;
    }
    if (principal.getName().equals(data.otherUsername())) {
      return selfRequestResponse;
    }

    try {
      nats.sendChatDestroying(data.chatId(), principal.getName(), data.otherUsername());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex); // NOSONAR
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @PostMapping("${api.send-chat-message}")
  ResponseEntity<StatusWrapper> sendChatMessage(HttpServletRequest request,
                                                @RequestBody ChatMessageData data,
                                                Principal principal) {
    logDebugHttpRequest(request, principal, data);
    if (!userService.isUserExistent(data.otherUsername())) {
      return nonExistentUserResponse;
    }
    if (principal.getName().equals(data.otherUsername())) {
      return selfRequestResponse;
    }
    if (data.messageData().length > textMaxByteLength) {
      return tooMuchDataResponse;
    }

    try {
      nats.sendChatMessage(data.messageId(), data.chatId(), principal.getName(),
          data.otherUsername(), data.messageData(), data.attachedFileName(), data.isImage());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex); // NOSONAR
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @PostMapping("${api.send-image}")
  ResponseEntity<StatusWrapper> sendImage(HttpServletRequest request,
                                          @RequestBody ChatImageData data,
                                          Principal principal) {
    logDebugHttpRequest(request, principal, data);
    if (!userService.isUserExistent(data.otherUsername())) {
      return nonExistentUserResponse;
    }
    if (principal.getName().equals(data.otherUsername())) {
      return selfRequestResponse;
    }

    if (data.imageData().length > filePartByteLength) {
      return tooMuchDataResponse;
    }

    try {
      nats.sendImage(data.messageId(), data.chatId(), principal.getName(),
          data.otherUsername(), data.fileName(), data.imageData());
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex); // NOSONAR
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @PostMapping("${api.send-file-part}")
  ResponseEntity<StatusWrapper> sendFilePart(HttpServletRequest request,
                                             @RequestBody ChatFileData data,
                                             Principal principal) {
    logDebugHttpRequest(request, principal, data);
    if (!userService.isUserExistent(data.otherUsername())) {
      return nonExistentUserResponse;
    }
    if (principal.getName().equals(data.otherUsername())) {
      return selfRequestResponse;
    }

    String fileName = data.chatId() + data.messageId();
    try {
      long pos = data.partNumber() * FILE_BLOCK_BYTE_SIZE;
      fileStorage.writeFilePart(fileName, pos, data.fileData());
    } catch (LocalFileStorageException ex) {
      log.error("File storage exception", ex);
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }

  @PostMapping("${api.request-message-file}")
  ResponseEntity<StatusWrapper> requestMessageFile(HttpServletRequest request,
                                                   @RequestBody MessageFileRequestData data,
                                                   Principal principal) {
    logDebugHttpRequest(request, principal, data);
    if (!userService.isUserExistent(data.otherUsername())) {
      return nonExistentUserResponse;
    }
    if (principal.getName().equals(data.otherUsername())) {
      return selfRequestResponse;
    }

    String fileName = data.chatId() + data.messageId();
    try {
      if (!fileStorage.isFileExist(fileName)) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new StatusWrapper(InternalStatus.RESOURCE_NOT_FOUND));
      }

      long fileSize = fileStorage.getFileSize(fileName);
      long partCnt = fileSize / FILE_BLOCK_BYTE_SIZE;
      if (fileSize % FILE_BLOCK_BYTE_SIZE != 0) {
        partCnt++;
      }

      byte[] readBlock = new byte[FILE_BLOCK_BYTE_SIZE];
      for (long i = 0; i < partCnt; ++i) {
        long pos = i * FILE_BLOCK_BYTE_SIZE;
        int read = fileStorage.readFilePart(fileName, pos, readBlock);

        byte[] block;
        if (read == FILE_BLOCK_BYTE_SIZE) {
          block = readBlock;
        } else {
          block = Arrays.copyOf(readBlock, read);
        }
        nats.sendFilePart(data.messageId(), data.chatId(), data.otherUsername(),
            principal.getName(), partCnt, i, block);
      }
    } catch (LocalFileStorageException ex) {
      log.error("File storage exception", ex);
      return internalErrorResponse;
    } catch (Exception ex) {
      log.error("Failed to send NATS event", ex); // NOSONAR
      return internalErrorResponse;
    }

    try {
      fileStorage.deleteFile(fileName);
    } catch (LocalFileStorageException ex) {
      log.warn("Failed to delete processed file", ex);
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }



  private void logDebugHttpRequest(HttpServletRequest request, Principal principal, Object body) {
    String username = principal != null ? principal.getName() : "anonymous";
    String method = "%4s".formatted(request.getMethod());
    String bodyString = null;
    if (body != null) {
      bodyString = body.toString();
      bodyString = bodyString.substring(0, Math.min(512, bodyString.length()));
    }
    log.debug("User {}: {} {} | body: {}", username, method, request.getRequestURL(), bodyString);
  }
}