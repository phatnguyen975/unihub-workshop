package com.unihub.workshop.workers.batch_ingestion.core.pipeline;

import com.unihub.workshop.workers.batch_ingestion.core.models.RejectedStudentRow;
import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.RejectArtifactStore;
import java.util.HashSet;
import java.util.Set;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class ChunkDuplicateResolver
    implements
        ItemProcessor<StudentDirectoryRow, StudentDirectoryRow>,
        ChunkListener<StudentDirectoryRow, StudentDirectoryRow>
{

    private final RejectArtifactStore rejectArtifactStore;
    private final Long jobExecutionId;
    private final Set<String> seenStudentNumbers = new HashSet<>();

    public ChunkDuplicateResolver(
        RejectArtifactStore rejectArtifactStore,
        @Value("#{stepExecution.jobExecutionId}") Long jobExecutionId
    ) {
        this.rejectArtifactStore = rejectArtifactStore;
        this.jobExecutionId = jobExecutionId;
    }

    @Override
    public void beforeChunk(Chunk<StudentDirectoryRow> chunk) {
        seenStudentNumbers.clear();
    }

    @Override
    public void afterChunk(Chunk<StudentDirectoryRow> chunk) {
        seenStudentNumbers.clear();
    }

    @Override
    public void onChunkError(Exception e, Chunk<StudentDirectoryRow> chunk) {
        seenStudentNumbers.clear();
    }

    @Override
    public StudentDirectoryRow process(StudentDirectoryRow item) {
        if (item == null || item.studentNumber() == null) {
            return item;
        }

        if (seenStudentNumbers.contains(item.studentNumber())) {
            RejectedStudentRow rejectedRow = new RejectedStudentRow(
                item.studentNumber(),
                item.email(),
                item.fullName(),
                item.faculty(),
                "Duplicate student number in chunk"
            );
            rejectArtifactStore.append(jobExecutionId, rejectedRow);
            return null;
        } else {
            seenStudentNumbers.add(item.studentNumber());
            return item;
        }
    }
}
