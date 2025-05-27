package org.reminstant.secretalk.server.service;

import org.reminstant.secretalk.server.exception.ConstraintViolationException;
import org.reminstant.secretalk.server.exception.InvalidPasswordException;
import org.reminstant.secretalk.server.exception.InvalidUsernameException;
import org.reminstant.secretalk.server.exception.OccupiedUsername;
import org.reminstant.secretalk.server.model.AppUser;
import org.reminstant.secretalk.server.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class AppUserService implements UserDetailsService {

  private final AppUserRepository appUserRepository;

  public AppUserService(AppUserRepository appUserRepository) {
    this.appUserRepository = appUserRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    Optional<AppUser> user = appUserRepository.getAppUserByUsername(username);

    if (user.isEmpty()) {
      throw new UsernameNotFoundException("username '%s' not found".formatted(username));
    }

    return User.builder()
        .username(user.get().getUsername())
        .password(user.get().getPassword())
        .roles(user.get().getRole())
        .build();
  }

  @Transactional
  public void registerUser(String username, String password) throws ConstraintViolationException {
    Objects.requireNonNull(username, "username cannot be null");
    Objects.requireNonNull(password, "username cannot be null");

    if (username.length() < 4 || username.length() > 16 || !username.matches("\\w+")) {
      throw new InvalidUsernameException();
    }
    if (password.length() < 6) {
      throw new InvalidPasswordException();
    }
    if (appUserRepository.existsAppUserByUsername(username)) {
      throw new OccupiedUsername();
    }

    String encryptedPassword = new BCryptPasswordEncoder().encode(password);
    appUserRepository.save(new AppUser(username, encryptedPassword));
  }
}
