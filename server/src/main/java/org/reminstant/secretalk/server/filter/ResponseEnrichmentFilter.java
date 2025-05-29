package org.reminstant.secretalk.server.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.reminstant.secretalk.server.util.InternalStatus;
import org.reminstant.secretalk.server.util.ObjectMappers;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

@Slf4j
@Component
public class ResponseEnrichmentFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
    filterChain.doFilter(request, responseWrapper);

    String contentType = responseWrapper.getContentType();
    if (contentType != null && contentType.equals(MediaType.APPLICATION_JSON_VALUE)) {
      Map<String, Object> data;
      if (responseWrapper.getContentAsByteArray().length != 0) {
        data = ObjectMappers.defaultObjectMapper
            .readValue(responseWrapper.getContentAsByteArray(), new TypeReference<>() {});
      } else {
        data = HashMap.newHashMap(2);
      }

      DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
      dateFormat.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
      String timestamp = dateFormat.format(new Date()).replace("Z", "+00:00");
      int internalStatus = InternalStatus.fromHttpStatus(responseWrapper.getStatus());

      data.putIfAbsent("timestamp", timestamp);
      data.putIfAbsent("internalStatus", internalStatus);

      responseWrapper.resetBuffer();
      responseWrapper.getWriter().write(ObjectMappers.defaultObjectMapper.writeValueAsString(data));
      responseWrapper.copyBodyToResponse();
    }
  }
}
