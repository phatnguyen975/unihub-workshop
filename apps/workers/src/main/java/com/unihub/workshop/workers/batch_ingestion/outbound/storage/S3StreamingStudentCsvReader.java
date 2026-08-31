package com.unihub.workshop.workers.batch_ingestion.outbound.storage;

import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;
import com.unihub.workshop.workers.batch_ingestion.core.pipeline.StreamingStudentCsvReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.io.InputStream;
import java.util.UUID;

@Component
public class S3StreamingStudentCsvReader implements StreamingStudentCsvReader {

    private final S3Client s3Client;
    private final String bucketName;
    private FlatFileItemReader<StudentDirectoryRow> delegate;
    private InputStream currentStream;

    public S3StreamingStudentCsvReader(S3Client s3Client, @Value("${app.storage.buckets.csv}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public void open(String sourceFileKey, UUID organizationId) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(sourceFileKey)
                .build();

        currentStream = s3Client.getObject(request, ResponseTransformer.toInputStream());

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("student_number", "email", "full_name", "faculty");

        DefaultLineMapper<StudentDirectoryRow> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);

        lineMapper.setFieldSetMapper(new FieldSetMapper<StudentDirectoryRow>() {
            @Override
            public StudentDirectoryRow mapFieldSet(FieldSet fieldSet) {
                return new StudentDirectoryRow(
                        organizationId,
                        fieldSet.readString("student_number"),
                        fieldSet.readString("email"),
                        fieldSet.readString("full_name"),
                        fieldSet.readString("faculty"));
            }
        });

        delegate = new FlatFileItemReader<>(new InputStreamResource(currentStream), lineMapper);
        delegate.setLinesToSkip(1); // skip header

        delegate.open(new ExecutionContext());
    }

    @Override
    public StudentDirectoryRow readRow() {
        try {
            return delegate.read();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read row", e);
        }
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
        }
        if (currentStream != null) {
            try {
                currentStream.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
