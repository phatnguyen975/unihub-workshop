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
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
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
        StudentCsvItemStreamReader studentCsvItemReader,
        CompositeItemProcessor<
            StudentDirectoryRow,
            StudentDirectoryRow
        > compositeProcessor,
        ChunkDuplicateResolver deduplicator,
        StudentDirectoryChunkItemWriter studentDirectoryItemWriter
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
    public CompositeItemProcessor<
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
    public StudentCsvItemStreamReader studentCsvItemReader(
        StreamingStudentCsvReader streamingReader,
        @Value("#{jobParameters['sourceFileKey']}") String sourceFileKey,
        @Value("#{jobParameters['organizationId']}") String orgId
    ) {
        return new StudentCsvItemStreamReader(
            streamingReader,
            sourceFileKey,
            UUID.fromString(orgId)
        );
    }

    @Bean
    @StepScope
    public StudentDirectoryChunkItemWriter studentDirectoryItemWriter(
        StudentDirectoryWriter writer,
        @Value("#{jobParameters['sourceFileKey']}") String sourceFileKey
    ) {
        return new StudentDirectoryChunkItemWriter(writer, sourceFileKey);
    }

    public static class StudentCsvItemStreamReader
        implements ItemStreamReader<StudentDirectoryRow>
    {

        private final StreamingStudentCsvReader streamingReader;
        private final String sourceFileKey;
        private final UUID organizationId;
        private boolean opened = false;

        public StudentCsvItemStreamReader(
            StreamingStudentCsvReader streamingReader,
            String sourceFileKey,
            UUID organizationId
        ) {
            this.streamingReader = streamingReader;
            this.sourceFileKey = sourceFileKey;
            this.organizationId = organizationId;
        }

        @Override
        public void open(ExecutionContext executionContext) {
            if (!opened) {
                streamingReader.open(sourceFileKey, organizationId);
                opened = true;
            }
        }

        @Override
        public void update(ExecutionContext executionContext) {}

        @Override
        public void close() {
            try {
                streamingReader.close();
            } catch (Exception ignored) {}
        }

        @Override
        public StudentDirectoryRow read() throws Exception {
            if (!opened) {
                open(new ExecutionContext());
            }
            StudentDirectoryRow row = streamingReader.readRow();
            if (row == null) {
                close();
            }
            return row;
        }
    }

    public static class StudentDirectoryChunkItemWriter
        implements ItemWriter<StudentDirectoryRow>
    {

        private final StudentDirectoryWriter writer;
        private final String sourceFileKey;

        public StudentDirectoryChunkItemWriter(
            StudentDirectoryWriter writer,
            String sourceFileKey
        ) {
            this.writer = writer;
            this.sourceFileKey = sourceFileKey;
        }

        @Override
        public void write(Chunk<? extends StudentDirectoryRow> chunk) {
            writer.bulkUpsert(new ArrayList<>(chunk.getItems()), sourceFileKey);
        }
    }
}
