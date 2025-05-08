package org.reminstant.cryptomessengerserver.dto.http;

public record ChatConnectionAcceptData(
    String chatId,
    String otherUsername,
    String publicKey) {
}
