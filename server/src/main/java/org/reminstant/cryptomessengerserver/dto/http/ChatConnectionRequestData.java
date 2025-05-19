package org.reminstant.cryptomessengerserver.dto.http;

import org.reminstant.cryptomessengerserver.dto.common.ChatConfiguration;

public record ChatConnectionRequestData(
    String chatId,
    String otherUsername,
    ChatConfiguration chatConfiguration,
    String publicKey) {
}
