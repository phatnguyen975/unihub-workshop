package com.unihub.workshop.workshops.application.ports.in;

public interface UploadBrochureUseCase {
    BrochureUploadResult uploadBrochure(UploadBrochureCommand command);
}
