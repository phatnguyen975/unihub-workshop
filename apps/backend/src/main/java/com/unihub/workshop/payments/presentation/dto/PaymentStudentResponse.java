package com.unihub.workshop.payments.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentStudentResponse(String fullName, String studentNumber) {}
