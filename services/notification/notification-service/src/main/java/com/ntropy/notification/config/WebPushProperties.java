package com.ntropy.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@Component
public class WebPushProperties {

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushProperties(
            @Value("${webpush.vapid.public-key}") String publicKey,
            @Value("${webpush.vapid.private-key}") String privateKey,
            @Value("${webpush.vapid.subject}") String subject
    ) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }
}
