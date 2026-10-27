package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.StudentDirectory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentDirectoryRepository {
    Optional<StudentDirectory> findActiveByEmail(String email);

    /**
     * Resolves student directory records in one batch by their identifiers.
     *
     * @param ids student directory identifiers
     * @return matching records; empty when no identifiers are supplied
     */
    List<StudentDirectory> findAllByIds(Collection<UUID> ids);
}
