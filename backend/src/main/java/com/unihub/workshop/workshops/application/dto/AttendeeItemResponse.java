package com.unihub.workshop.workshops.application.dto;

public record AttendeeItemResponse(
        String id,
        String studentId,
        String fullName,
        String email,
        String status) {
}
