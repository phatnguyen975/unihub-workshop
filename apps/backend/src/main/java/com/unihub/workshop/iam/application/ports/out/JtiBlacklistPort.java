package com.unihub.workshop.iam.application.ports.out;

import java.time.Duration;

public interface JtiBlacklistPort {
    void blacklistJti(String jti, Duration ttl);
}
