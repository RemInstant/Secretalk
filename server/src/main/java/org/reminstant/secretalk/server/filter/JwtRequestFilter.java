package org.reminstant.secretalk.server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.reminstant.secretalk.server.service.JwtService;
import org.reminstant.secretalk.server.util.ObjectMappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;


@Component
public class JwtRequestFilter extends OncePerRequestFilter {

  private final RequestMatcher requestMatcher;
  private final SecurityContextRepository securityContextRepository;
  private final JwtService jwtService;
  private final AuthenticationEntryPoint entryPoint;

  public JwtRequestFilter(JwtService jwtService, AuthenticationEntryPoint entryPoint,
                          @Value("${api.login}") String loginEndpoint) {
    this.jwtService = jwtService;
    this.entryPoint = entryPoint;
    requestMatcher = new AntPathRequestMatcher(loginEndpoint, HttpMethod.POST.name());
    securityContextRepository = new RequestAttributeSecurityContextRepository();
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain
  ) throws ServletException, IOException {
    if (!requestMatcher.matches(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    if (securityContextRepository.containsContext(request)) {
      SecurityContext context = securityContextRepository.loadDeferredContext(request).get();
      if (context != null && context.getAuthentication() != null &&
          !(context.getAuthentication() instanceof PreAuthenticatedAuthenticationToken)) {
        if (context.getAuthentication().getPrincipal() instanceof UserDetails userDetails) {
          String jwtToken = jwtService.generateToken(userDetails, Duration.ofDays(1));

          response.setStatus(HttpServletResponse.SC_OK);
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          response.getWriter().write(ObjectMappers.defaultObjectMapper
              .writeValueAsString(Map.of("token", jwtToken)));
          return;
        }
      }
    }

    entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));
  }
}
