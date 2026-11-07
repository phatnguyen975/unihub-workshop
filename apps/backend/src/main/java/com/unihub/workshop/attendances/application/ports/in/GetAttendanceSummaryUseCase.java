package com.unihub.workshop.attendances.application.ports.in;

import com.unihub.workshop.attendances.domain.model.AttendanceSummary;

public interface GetAttendanceSummaryUseCase {
    AttendanceSummary execute(GetAttendanceSummaryQuery query);
}
