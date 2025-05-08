package org.reminstant.cryptomessengerserver.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String username;
  private String password;
  private String role;

  public AppUser(String username, String password) {
    this.username = username;
    this.password = password;
    this.role = "USER";
  }
}
