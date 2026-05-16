package com.unihub.workshop.workshops.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;

public interface WorkshopAssignmentRepository extends JpaRepository<WorkshopAssignment, UUID> {

    @Query("""
                SELECT a FROM WorkshopAssignment a
                JOIN a.workshop w
                WHERE a.staffId = :staffId
                    AND w.startTime < :end
                    AND w.endTime > :start
            """)
    List<WorkshopAssignment> findOverlappingAssignments(@Param("staffId") UUID staffId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    java.util.Optional<WorkshopAssignment> findByStaffIdAndWorkshopId(java.util.UUID staffId, java.util.UUID workshopId);
}
