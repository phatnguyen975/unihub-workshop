package com.unihub.workshop.attendances.application.usecases;

import com.unihub.workshop.attendances.application.ports.in.GetAttendanceSummaryQuery;
import com.unihub.workshop.attendances.application.ports.in.GetAttendanceSummaryUseCase;
import com.unihub.workshop.attendances.application.ports.out.AttendanceRepository;
import com.unihub.workshop.attendances.domain.model.AttendanceSummary;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GetAttendanceSummaryUseCaseImpl
    implements GetAttendanceSummaryUseCase
{

    private final WorkshopApi workshopApi;
    private final RegistrationApi registrationApi;
    private final AttendanceRepository attendanceRepository;
    private final TransactionTemplate primary;
    private final TransactionTemplate replica;

    public GetAttendanceSummaryUseCaseImpl(
        WorkshopApi workshopApi,
        RegistrationApi registrationApi,
        AttendanceRepository attendanceRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.workshopApi = workshopApi;
        this.registrationApi = registrationApi;
        this.attendanceRepository = attendanceRepository;
        primary = new TransactionTemplate(transactionManager);
        primary.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        replica = new TransactionTemplate(transactionManager);
        replica.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        replica.setReadOnly(true);
    }

    @Override
    public AttendanceSummary execute(GetAttendanceSummaryQuery query) {
        try {
            return readSummary(query);
        } catch (RuntimeException failure) {
            if (isConnectionFailure(failure)) {
                throw new ServiceUnavailableException(
                    "Attendance summary is temporarily unavailable. Please retry.",
                    failure
                );
            }
            throw failure;
        }
    }

    private AttendanceSummary readSummary(GetAttendanceSummaryQuery query) {
        WorkshopRegistrationDetailsDto workshop = primary.execute(status -> {
            WorkshopRegistrationDetailsDto found = workshopApi
                .getWorkshopForRegistration(query.workshopId())
                .orElseThrow(() -> new NotFoundException("Workshop not found"));
            boolean authorized = switch (
                query.role() != null ? query.role() : ""
            ) {
                case "ORGANIZER" -> query.organizationId() != null &&
                    query.organizationId().equals(found.organizationId());
                case "STAFF" -> query.userId() != null &&
                    workshopApi.isStaffAssignedToWorkshop(
                        query.workshopId(),
                        query.userId()
                    );
                default -> false;
            };
            if (!authorized) {
                throw new ForbiddenException("Access denied");
            }
            return found;
        });
        return replica.execute(status ->
            AttendanceSummary.of(
                query.workshopId(),
                workshop.capacity() != null ? workshop.capacity() : 0,
                registrationApi.countConfirmedByWorkshopId(query.workshopId()),
                attendanceRepository.countValidByWorkshopId(query.workshopId()),
                attendanceRepository.findRecentValidByWorkshopId(
                    query.workshopId()
                )
            )
        );
    }

    private static boolean isConnectionFailure(Throwable failure) {
        for (
            Throwable cause = failure;
            cause != null;
            cause = cause.getCause()
        ) {
            if (
                cause instanceof DataAccessResourceFailureException ||
                cause instanceof JDBCConnectionException
            ) {
                return true;
            }
            if (cause instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                if (
                    sqlException instanceof SQLTransientConnectionException ||
                    sqlException instanceof
                        SQLNonTransientConnectionException ||
                    sqlException instanceof SQLRecoverableException ||
                    (state != null && state.startsWith("08"))
                ) {
                    return true;
                }
            }
        }
        return false;
    }
}
