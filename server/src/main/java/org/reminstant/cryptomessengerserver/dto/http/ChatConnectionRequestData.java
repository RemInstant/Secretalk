package org.reminstant.cryptomessengerserver.dto.http;

public record ChatConnectionRequestData(
    String chatId,
    String otherUsername,
    String publicKey) {
}
