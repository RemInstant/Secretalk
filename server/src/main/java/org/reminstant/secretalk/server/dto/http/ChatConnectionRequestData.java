package org.reminstant.secretalk.server.dto.http;

import org.reminstant.secretalk.server.dto.common.ChatConfiguration;

public record ChatConnectionRequestData(
    String chatId,
    String otherUsername,
    ChatConfiguration chatConfiguration,
    String publicKey) {
}
