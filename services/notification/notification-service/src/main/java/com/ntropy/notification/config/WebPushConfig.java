package com.ntropy.notification.config;

import java.security.GeneralSecurityException;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import nl.martijndwars.webpush.PushService;

@Configuration
@PropertySource(value = "classpath:webpush-local.properties", ignoreResourceNotFound = true)
public class WebPushConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer webPushPropertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public PushService pushService(WebPushProperties properties) throws GeneralSecurityException {
        Security.addProvider(new BouncyCastleProvider());
        PushService pushService = new PushService(properties.getPublicKey(), properties.getPrivateKey());
        pushService.setSubject(properties.getSubject());
        return pushService;
    }
}
