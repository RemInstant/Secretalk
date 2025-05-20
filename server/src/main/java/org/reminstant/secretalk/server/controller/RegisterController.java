package org.reminstant.secretalk.server.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.secretalk.server.exception.InvalidCredentials;
import org.reminstant.secretalk.server.exception.OccupiedUsername;
import org.reminstant.secretalk.server.service.AppUserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
public class RegisterController {

  private final AppUserService appUserService;

  public RegisterController(AppUserService appUserService) {
    this.appUserService = appUserService;
  }

  @PostMapping(value = "${api.register}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> register(@RequestBody Map<String, String> data, Principal principal) {
    String username = data.getOrDefault("username", null);
    String password = data.getOrDefault("password", null);
    int status;

    if (principal != null) {
      status = HttpServletResponse.SC_BAD_REQUEST;
    } else {
      try {
        appUserService.registerUser(username, password);
        status = HttpServletResponse.SC_OK;
      } catch (OccupiedUsername e) {
        status = HttpServletResponse.SC_BAD_REQUEST;
      } catch (InvalidCredentials e) {
        status = HttpServletResponse.SC_BAD_REQUEST;
      }
    }

    // TODO: custom statuses

    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .build();
  }
}
