package com.unihub.workshop.workers.batch_ingestion.inbound;

import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;
import com.unihub.workshop.workers.batch_ingestion.core.pipeline.ChunkDuplicateResolver;
import com.unihub.workshop.workers.batch_ingestion.core.pipeline.StreamingStudentCsvReader;
import com.unihub.workshop.workers.batch_ingestion.core.pipeline.StudentRowValidator;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.StudentDirectoryWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class StudentCsvBatchJobConfiguration {

    @Bean
    public Job cleanCsvIngestionJob(
        JobRepository jobRepository,
        Step processCsvStep
    ) {
        return new JobBuilder("cleanCsvIngestionJob", jobRepository)
            .start(processCsvStep)
            .build();
    }

    @Bean
    public Step processCsvStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        ItemReader<StudentDirectoryRow> studentCsvItemReader,
        ItemProcessor<
            StudentDirectoryRow,
            StudentDirectoryRow
        > compositeProcessor,
        ChunkDuplicateResolver deduplicator,
        ItemWriter<StudentDirectoryRow> studentDirectoryItemWriter
    ) {
        return new StepBuilder("processCsvStep", jobRepository)
            .<StudentDirectoryRow, StudentDirectoryRow>chunk(100)
            .transactionManager(transactionManager)
            .reader(studentCsvItemReader)
            .processor(compositeProcessor)
            .writer(studentDirectoryItemWriter)
            .listener(deduplicator)
            .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<
        StudentDirectoryRow,
        StudentDirectoryRow
    > compositeProcessor(
        StudentRowValidator validator,
        ChunkDuplicateResolver deduplicator
    ) {
        CompositeItemProcessor<
            StudentDirectoryRow,
            StudentDirectoryRow
        > processor = new CompositeItemProcessor<>();
        processor.setDelegates(List.of(validator, deduplicator));
        return processor;
    }

    @Bean
    @StepScope
    public ItemReader<StudentDirectoryRow> studentCsvItemReader(
        StreamingStudentCsvReader streamingReader,
        @Value("#{jobParameters['sourceFileKey']}") String sourceFileKey,
        @Value("#{jobParameters['organizationId']}") String orgId
    ) {
        return new ItemReader<>() {
            private boolean opened = false;

            @Override
            public StudentDirectoryRow read() throws Exception {
                if (!opened) {
                    streamingReader.open(sourceFileKey, UUID.fromString(orgId));
                    opened = true;
                }
                return streamingReader.readRow();
            }
        };
    }

    @Bean
    @StepScope
    public ItemWriter<StudentDirectoryRow> studentDirectoryItemWriter(
        StudentDirectoryWriter writer,
        @Value("#{jobParameters['sourceFileKey']}") String sourceFileKey
    ) {
        return chunk -> {
            List<StudentDirectoryRow> rows = new ArrayList<>();
            for (Object item : chunk.getItems()) {
                rows.add((StudentDirectoryRow) item);
            }
            writer.bulkUpsert(rows, sourceFileKey);
        };
    }
}
