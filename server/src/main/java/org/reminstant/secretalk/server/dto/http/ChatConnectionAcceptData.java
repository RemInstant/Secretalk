package org.reminstant.secretalk.server.dto.http;

public record ChatConnectionAcceptData(
    String chatId,
    String otherUsername,
    String publicKey) {
}
