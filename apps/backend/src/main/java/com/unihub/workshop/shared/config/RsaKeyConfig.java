package com.unihub.workshop.shared.config;

import com.unihub.workshop.shared.security.RsaKeyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.core.io.Resource;
import org.springframework.core.io.DefaultResourceLoader;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableConfigurationProperties(RsaKeyProperties.class)
public class RsaKeyConfig {

    public RsaKeyConfig(RsaKeyProperties properties) {
        if (properties.privateKey() == null || properties.publicKey() == null) {
            throw new IllegalStateException("RSA keys must be provided at startup. Missing public or private key.");
        }
    }

    @Bean
    @ConfigurationPropertiesBinding
    public static Converter<String, RSAPublicKey> rsaPublicKeyConverter() {
        return new Converter<String, RSAPublicKey>() {
            @Override
            public RSAPublicKey convert(String source) {
                try {
                    Resource resource = new DefaultResourceLoader().getResource(source);
                    return RsaKeyConverters.x509().convert(resource.getInputStream());
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot read public key from " + source, e);
                }
            }
        };
    }

    @Bean
    @ConfigurationPropertiesBinding
    public static Converter<String, RSAPrivateKey> rsaPrivateKeyConverter() {
        return new Converter<String, RSAPrivateKey>() {
            @Override
            public RSAPrivateKey convert(String source) {
                try {
                    Resource resource = new DefaultResourceLoader().getResource(source);
                    return RsaKeyConverters.pkcs8().convert(resource.getInputStream());
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot read private key from " + source, e);
                }
            }
        };
    }
}
