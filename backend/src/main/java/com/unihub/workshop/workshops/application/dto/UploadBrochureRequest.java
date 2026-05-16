package com.unihub.workshop.workshops.application.dto;

import org.springframework.web.multipart.MultipartFile;

public record UploadBrochureRequest(MultipartFile file) {
}
