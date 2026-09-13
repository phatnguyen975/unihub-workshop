package com.unihub.workshop.workers.batch_ingestion.core.models;

public record RejectedStudentRow(
    String studentNumber,
    String email,
    String fullName,
    String faculty,
    String rejectReason
) {}
