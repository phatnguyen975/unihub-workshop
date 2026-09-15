package com.unihub.workshop.shared.security;

import java.util.List;
import lombok.Builder;

@Builder
public record JwksResponse(List<Jwk> keys) {
    @Builder
    public record Jwk(String kty, String e, String use, String n, String alg) {}
}
