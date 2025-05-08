package org.reminstant.cryptomessengerserver.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.reminstant.cryptomessengerserver.service.JwtService;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String AUTHORIZATION_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final AuthenticationEntryPoint entryPoint;

  public JwtAuthenticationFilter(JwtService jwtService, AuthenticationEntryPoint entryPoint) {
    this.jwtService = jwtService;
    this.entryPoint = entryPoint;
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain
  ) throws ServletException, IOException {
    String tokenBearer = request.getHeader(AUTHORIZATION_HEADER);

    if (tokenBearer != null && tokenBearer.startsWith(AUTHORIZATION_PREFIX)) {
      String token = tokenBearer.substring(AUTHORIZATION_PREFIX.length());

      if (!jwtService.isTokenValid(token)) {
        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));
        return;
      }

      String username = jwtService.extractUsername(token);
      PreAuthenticatedAuthenticationToken authToken = new PreAuthenticatedAuthenticationToken(
          username, token, List.of(new SimpleGrantedAuthority("ROLE_USER")));
      SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    filterChain.doFilter(request, response);
  }
}
