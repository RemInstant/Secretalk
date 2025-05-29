package org.reminstant.secretalk.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.reminstant.secretalk.server.dto.http.StatusWrapper;
import org.reminstant.secretalk.server.dto.http.RegisterData;
import org.reminstant.secretalk.server.exception.ConstraintViolationException;
import org.reminstant.secretalk.server.exception.InvalidPasswordException;
import org.reminstant.secretalk.server.exception.InvalidUsernameException;
import org.reminstant.secretalk.server.exception.OccupiedUsername;
import org.reminstant.secretalk.server.service.AppUserService;
import org.reminstant.secretalk.server.util.InternalStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class RegisterController {

  private final AppUserService appUserService;

  public RegisterController(AppUserService appUserService) {
    this.appUserService = appUserService;
  }

  @PostMapping(value = "${api.register}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<StatusWrapper> register(@RequestBody RegisterData data) {
    int status = InternalStatus.OK;

    try {
      appUserService.registerUser(data.username(), data.password());
    } catch (OccupiedUsername _) {
      status = InternalStatus.OCCUPIED_USERNAME;
    } catch (InvalidUsernameException _) {
      status = InternalStatus.INVALID_USERNAME;
    } catch (InvalidPasswordException _) {
      status = InternalStatus.INVALID_PASSWORD;
    } catch (ConstraintViolationException _) {
      status = InternalStatus.INVALID_CREDENTIALS;
    }

    return ResponseEntity.status(InternalStatus.toHttpStatus(status))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new StatusWrapper(status));
  }
}
