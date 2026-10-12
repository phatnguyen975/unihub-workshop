package com.unihub.workshop.workshops.application.ports.out;

import java.io.InputStream;

public interface BrochureStoragePort {
    void uploadBrochure(
        String storageKey,
        InputStream inputStream,
        long byteSize,
        String contentType
    );
}
