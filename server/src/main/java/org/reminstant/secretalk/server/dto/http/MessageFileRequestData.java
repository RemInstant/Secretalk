package org.reminstant.secretalk.server.dto.http;

public record MessageFileRequestData(
    String messageId,
    String chatId,
    String otherUsername) {
}
