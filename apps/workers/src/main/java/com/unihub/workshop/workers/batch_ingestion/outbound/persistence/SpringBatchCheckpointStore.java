package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionMetrics;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionCheckpointStore;
import java.util.UUID;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Component
public class SpringBatchCheckpointStore implements IngestionCheckpointStore {

    private final JobOperator jobOperator;
    private final Job cleanCsvIngestionJob;
    private final JobRepository jobRepository;

    public SpringBatchCheckpointStore(
        JobOperator jobOperator,
        Job cleanCsvIngestionJob,
        JobRepository jobRepository
    ) {
        this.jobOperator = jobOperator;
        this.cleanCsvIngestionJob = cleanCsvIngestionJob;
        this.jobRepository = jobRepository;
    }

    @Override
    public long startIngestionJob(
        String sourceFileKey,
        UUID organizationId,
        String organizationSlug,
        String organizationName
    ) {
        JobParameters params = new JobParametersBuilder()
            .addString("sourceFileKey", sourceFileKey)
            .addString("organizationId", organizationId.toString())
            .addString("organizationSlug", organizationSlug)
            .addString("organizationName", organizationName)
            .addLong("time", System.currentTimeMillis())
            .toJobParameters();

        try {
            JobExecution execution = jobOperator.run(
                cleanCsvIngestionJob,
                params
            );
            return execution.getId();
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to launch batch job for file: " + sourceFileKey,
                e
            );
        }
    }

    @Override
    public IngestionMetrics getMetrics(long jobExecutionId) {
        JobExecution execution = jobRepository.getJobExecution(jobExecutionId);
        if (execution == null) {
            throw new IllegalArgumentException(
                "Job execution not found for ID: " + jobExecutionId
            );
        }

        int processed = 0;
        int rejected = 0;

        for (StepExecution step : execution.getStepExecutions()) {
            processed += step.getWriteCount();
            rejected += step.getFilterCount();
        }

        return new IngestionMetrics(processed, rejected);
    }
}
