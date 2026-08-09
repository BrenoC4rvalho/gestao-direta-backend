package br.com.gestaodireta.messaging.service;

public enum MessagingLinkResult {
    LINKED,
    LINK_CODE_NOT_FOUND,
    LINK_CODE_EXPIRED,
    TEMPORARILY_BLOCKED,
    ALREADY_LINKED
}
