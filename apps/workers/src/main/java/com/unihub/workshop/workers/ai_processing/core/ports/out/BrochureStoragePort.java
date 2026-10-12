package com.unihub.workshop.workers.ai_processing.core.ports.out;

public interface BrochureStoragePort {
    byte[] fetchBrochurePdf(String storageKey);
}
