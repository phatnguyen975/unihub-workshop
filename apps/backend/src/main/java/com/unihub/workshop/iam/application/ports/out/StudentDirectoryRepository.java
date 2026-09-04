package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.StudentDirectory;

import java.util.Optional;

public interface StudentDirectoryRepository {
    Optional<StudentDirectory> findByEmail(String email);
}
