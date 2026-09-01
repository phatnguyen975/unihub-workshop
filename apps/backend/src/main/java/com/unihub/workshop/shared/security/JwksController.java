package com.unihub.workshop.shared.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.unihub.workshop.shared.config.RsaKeyProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class JwksController {

    private final RsaKeyProperties rsaKeyProperties;

    public JwksController(RsaKeyProperties rsaKeyProperties) {
        this.rsaKeyProperties = rsaKeyProperties;
    }

    @GetMapping("/.well-known/jwks.json")
    public JwksResponse getJwks() {
        RSAKey rsaKey = new RSAKey.Builder(rsaKeyProperties.publicKey())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        JwksResponse.Jwk jwk = new JwksResponse.Jwk(
                rsaKey.getKeyType().getValue(),
                rsaKey.getPublicExponent().toString(),
                rsaKey.getKeyUse().identifier(),
                rsaKey.getModulus().toString(),
                rsaKey.getAlgorithm().getName());

        return new JwksResponse(List.of(jwk));
    }
}
