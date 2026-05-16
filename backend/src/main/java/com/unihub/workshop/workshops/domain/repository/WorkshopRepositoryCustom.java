package com.unihub.workshop.workshops.domain.repository;

import com.unihub.workshop.workshops.application.usecases.WorkshopDiscoveryCriteria;
import com.unihub.workshop.workshops.domain.model.Workshop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WorkshopRepositoryCustom {

    Page<Workshop> searchPublishedWorkshops(WorkshopDiscoveryCriteria criteria, Pageable pageable);
}
