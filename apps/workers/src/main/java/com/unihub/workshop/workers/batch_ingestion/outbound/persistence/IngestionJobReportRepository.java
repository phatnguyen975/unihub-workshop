package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionJobReportRepository
    extends JpaRepository<IngestionJobReportEntity, UUID> {}
