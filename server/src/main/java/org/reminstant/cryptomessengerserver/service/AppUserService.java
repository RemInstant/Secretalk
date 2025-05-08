package org.reminstant.cryptomessengerserver.service;

import org.reminstant.cryptomessengerserver.exception.InvalidCredentials;
import org.reminstant.cryptomessengerserver.exception.OccupiedUsername;
import org.reminstant.cryptomessengerserver.model.AppUser;
import org.reminstant.cryptomessengerserver.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  public void registerUser(String username, String password) throws InvalidCredentials, OccupiedUsername {
    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      throw new InvalidCredentials("Empty credentials");
    }

    if (username.length() < 4 || password.length() < 6) {
      throw new InvalidCredentials("Too short credentials");
    }

    if (username.length() > 20 || password.length() > 32) {
      throw new InvalidCredentials("Too long credentials");
    }

    if (!username.matches("\\w+")) {
      throw new InvalidCredentials("Username does not matches [0-9A-z_]");
    }

    if (appUserRepository.existsAppUserByUsername(username)) {
      throw new OccupiedUsername();
    }

    String encryptedPassword = new BCryptPasswordEncoder().encode(password);
    appUserRepository.save(new AppUser(username, encryptedPassword));
  }
}
