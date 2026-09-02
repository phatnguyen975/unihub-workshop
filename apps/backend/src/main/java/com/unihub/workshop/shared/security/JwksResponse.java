package com.unihub.workshop.shared.security;

import java.util.List;

public record JwksResponse(List<Jwk> keys) {

    public record Jwk(
            String kty,
            String e,
            String use,
            String n,
            String alg) {
    }
}
