package com.unihub.workshop.attendances.application.ports.out;

public interface DatabaseFailureClassifier {
    boolean isUnavailable(Throwable failure);
}
