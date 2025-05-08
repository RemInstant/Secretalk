package org.reminstant.cryptomessengerserver.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

@Getter
@Setter
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private String realmName;

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
                       AuthenticationException authException) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader("WWW-Authenticate", "Basic realm=\"%s\"".formatted(realmName));
    response.addHeader("WWW-Authenticate", "Bearer");
  }
}
