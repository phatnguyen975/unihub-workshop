package com.unihub.workshop.iam.domain.events;

public record UserRegisteredEvent(String email, String fullName) {
}
