package com.unihub.workshop.shared.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rsa")
public record RsaKeyProperties(
    RSAPrivateKey privateKey,
    RSAPublicKey publicKey
) {}
