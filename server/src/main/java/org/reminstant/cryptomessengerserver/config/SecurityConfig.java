package org.reminstant.cryptomessengerserver.config;

import lombok.extern.slf4j.Slf4j;
import org.reminstant.cryptomessengerserver.filter.CustomAuthenticationEntryPoint;
import org.reminstant.cryptomessengerserver.filter.JwtAuthenticationFilter;
import org.reminstant.cryptomessengerserver.filter.JwtRequestFilter;
import org.reminstant.cryptomessengerserver.filter.ResponseEnrichmentFilter;
import org.reminstant.cryptomessengerserver.service.AppUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.session.DisableEncodeUrlFilter;

import java.util.List;

@Slf4j
@Configuration
public class SecurityConfig {

  private final ApplicationContext applicationContext;

  public SecurityConfig(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity httpSecurityBuilder,
                                          @Value("${api.register}") String registerEndpoint,
                                          @Value("${api.get-dh-params}") String getDHParamsEndpoint)
      throws Exception {
    var entryPoint = applicationContext.getBean(AuthenticationEntryPoint.class);
    var responseEnrichmentFilter = applicationContext.getBean(ResponseEnrichmentFilter.class);
    var jwtAuthenticationFilter = applicationContext.getBean(JwtAuthenticationFilter.class);
    var jwtRequestFilter = applicationContext.getBean(JwtRequestFilter.class);

    return httpSecurityBuilder
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(c -> c
            .authenticationEntryPoint(entryPoint))
//        .authenticationManager(authenticationManager)
        .sessionManagement(s -> s
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/long-polling").permitAll()
            .requestMatchers(HttpMethod.GET, "/sse").permitAll()
            .requestMatchers(HttpMethod.GET, "/websocket").permitAll()
            .requestMatchers(HttpMethod.POST, "/kafka/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/nats/**").permitAll()
//            .requestMatchers("/api/chat/**").permitAll()
            .requestMatchers(HttpMethod.POST, registerEndpoint).permitAll()
            .requestMatchers(HttpMethod.GET, getDHParamsEndpoint).permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(responseEnrichmentFilter, DisableEncodeUrlFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, BasicAuthenticationFilter.class)
        .addFilterAfter(jwtRequestFilter, ExceptionTranslationFilter.class)
        .build();

//    DisableEncodeUrlFilter,
//    WebAsyncManagerIntegrationFilter,
//    SecurityContextHolderFilter,
//    HeaderWriterFilter,
//    LogoutFilter,
//    JwtAuthenticationFilter,
//    BasicAuthenticationFilter,
//    RequestCacheAwareFilter,
//    SecurityContextHolderAwareRequestFilter,
//    AnonymousAuthenticationFilter,
//    SessionManagementFilter,
//    ExceptionTranslationFilter,
//    JwtRequestFilter,
//    AuthorizationFilter
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

//  @Bean
//  public AuthenticationManager authManager(HttpSecurity httpSecurityBuilder) throws Exception {
//    UserDetailsService userDetailsService = applicationContext.getBean(UserDetailsService.class);
//
//    DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
//    authenticationProvider.setUserDetailsService(userDetailsService);
//    authenticationProvider.setPasswordEncoder(passwordEncoder());
//
//    var builder = httpSecurityBuilder.getSharedObject(AuthenticationManagerBuilder.class);
//    var q = builder
//        .authenticationProvider(authenticationProvider)
//        .build();
//    return q;
//  }


//  @Bean
//  AuthenticationManager authenticationManager(AuthenticationManagerBuilder builder) throws Exception {
//    UserDetailsService userDetailsService = applicationContext.getBean(UserDetailsService.class);
//
//    DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
//    authenticationProvider.setUserDetailsService(userDetailsService);
//    authenticationProvider.setPasswordEncoder(passwordEncoder());
//
//    return builder
//        .authenticationProvider(authenticationProvider)
//        .build();
//  }

//  @Bean
//  InMemoryUserDetailsManager userDetailsService() {
//    InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
//    manager.createUser(new User("user", passwordEncoder().encode("user"),
//        List.of(new SimpleGrantedAuthority("USER"))));
//
//    return manager;
//  }

  @Bean
  AuthenticationEntryPoint authenticationEntryPoint() {
    CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint();
    entryPoint.setRealmName("default");
    return entryPoint;
  }
}
