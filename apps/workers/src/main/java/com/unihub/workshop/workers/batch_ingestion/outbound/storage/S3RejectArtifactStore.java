package com.unihub.workshop.workers.batch_ingestion.outbound.storage;

import com.unihub.workshop.workers.batch_ingestion.core.models.RejectedStudentRow;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.RejectArtifactStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class S3RejectArtifactStore implements RejectArtifactStore {

    private final S3Client s3Client;
    private final String bucketName;
    private final ConcurrentHashMap<Long, File> jobExecutionFiles = new ConcurrentHashMap<>();

    public S3RejectArtifactStore(
            S3Client s3Client,
            @Value("${app.storage.buckets.csv}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    private File getFile(long jobExecutionId) {
        return jobExecutionFiles.computeIfAbsent(jobExecutionId, id -> {
            try {
                Path dir = Paths.get("apps", "workers", "resources");
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
                File file = dir.resolve("rejected_" + id + ".csv").toFile();
                try (FileWriter writer = new FileWriter(file, true)) {
                    writer.write("student_number,email,full_name,faculty,reject_reason\n");
                }
                return file;
            } catch (IOException e) {
                throw new RuntimeException("Could not create local reject artifact file", e);
            }
        });
    }

    @Override
    public void append(long jobExecutionId, RejectedStudentRow row) {
        File file = getFile(jobExecutionId);
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(String.format("%s,%s,%s,%s,%s\n",
                    escapeCsv(row.studentNumber()),
                    escapeCsv(row.email()),
                    escapeCsv(row.fullName()),
                    escapeCsv(row.faculty()),
                    escapeCsv(row.rejectReason())));
        } catch (IOException e) {
            throw new RuntimeException("Could not write to local reject artifact file", e);
        }
    }

    @Override
    public void uploadAndCleanUp(long jobExecutionId, String organizationSlug) {
        File file = jobExecutionFiles.remove(jobExecutionId);
        if (file != null && file.exists()) {
            String objectKey = String.format("rejected/%s/row-reports/%d.csv", organizationSlug, jobExecutionId);

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.putObject(putRequest, file.toPath());

            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
