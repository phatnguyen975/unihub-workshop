package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.workshops.domain.model.AiSummaryJob;

public interface GetBrochureSummaryJobUseCase {
    AiSummaryJob getSummaryJob(GetBrochureSummaryJobQuery query);
}
