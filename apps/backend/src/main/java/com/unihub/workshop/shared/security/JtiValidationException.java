package com.unihub.workshop.shared.security;

import org.springframework.security.core.AuthenticationException;

public class JtiValidationException extends AuthenticationException {
    public JtiValidationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
