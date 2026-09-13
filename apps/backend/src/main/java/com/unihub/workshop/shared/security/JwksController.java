package com.unihub.workshop.shared.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.unihub.workshop.shared.exception.InternalServerException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final RsaKeyProperties rsaKeyProperties;

    @GetMapping("/.well-known/jwks.json")
    public JwksResponse getJwks() {
        if (rsaKeyProperties == null || rsaKeyProperties.publicKey() == null) {
            throw new InternalServerException("RSA public key is not loaded.");
        }

        RSAKey rsaKey = new RSAKey.Builder(rsaKeyProperties.publicKey())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();

        JwksResponse.Jwk jwk = new JwksResponse.Jwk(
            rsaKey.getKeyType().getValue(),
            rsaKey.getPublicExponent().toString(),
            rsaKey.getKeyUse().identifier(),
            rsaKey.getModulus().toString(),
            rsaKey.getAlgorithm().getName()
        );

        return new JwksResponse(List.of(jwk));
    }
}
