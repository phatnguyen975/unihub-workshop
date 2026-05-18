package com.unihub.workshop.registrations.contracts.impl;

import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import com.unihub.workshop.registrations.contracts.AnalyticsData;
import com.unihub.workshop.registrations.contracts.AttendeeItem;
import com.unihub.workshop.registrations.contracts.AttendeePage;
import com.unihub.workshop.registrations.contracts.RegistrationWorkshopFacade;
import com.unihub.workshop.registrations.repository.RegistrationRepository;
import com.unihub.workshop.workshops.domain.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Real implementation of RegistrationWorkshopFacade backed by the registrations repository.
 */
@Service
@RequiredArgsConstructor
public class RegistrationWorkshopFacadeImpl implements RegistrationWorkshopFacade {

    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final WorkshopRepository workshopRepository;

    @Override
    public AnalyticsData getWorkshopAnalytics(UUID workshopId) {
        var workshopOpt = workshopRepository.findById(workshopId);
        int capacity = workshopOpt.map(w -> w.getCapacityTotal()).orElse(0);
        long totalRegistered = registrationRepository.findByWorkshopId(workshopId).size();
        long confirmed = registrationRepository.countByWorkshopIdAndStatus(workshopId, "CONFIRMED");
        long pending = registrationRepository.countByWorkshopIdAndStatus(workshopId, "PENDING");
        double utilization = capacity == 0 ? 0.0 : (confirmed * 100.0) / capacity;
        double confirmationRate = totalRegistered == 0 ? 0.0 : (confirmed * 100.0) / (double) totalRegistered;

        return new AnalyticsData(capacity, (int) totalRegistered, (int) confirmed, (int) pending, 0, 0, utilization, confirmationRate);
    }

    @Override
    public int getConfirmedRegistrationCount(UUID workshopId) {
        return (int) registrationRepository.countByWorkshopIdAndStatus(workshopId, "CONFIRMED");
    }

    @Override
    public int getPendingRegistrationCount(UUID workshopId) {
        return (int) registrationRepository.countByWorkshopIdAndStatus(workshopId, "PENDING");
    }

    @Override
    public AttendeePage listAttendees(UUID workshopId, int page, int size, String status) {
        var regs = registrationRepository.findByWorkshopId(workshopId);
        var items = regs.stream().map(r -> {
            UUID uid = r.getUserId();
            User user = userRepository.findById(uid).orElse(null);
            String studentId = user != null ? user.getStudentId() : null;
            String fullName = user != null ? user.getFullName() : null;
            String email = user != null ? user.getEmail() : null;
            return new AttendeeItem(r.getId().toString(), studentId, fullName, email, r.getStatus());
        }).skip((long) page * size).limit(size).collect(Collectors.toList());

        var pagination = new com.unihub.workshop.shared.web.PaginationMetadata(page, size, (long) regs.size(), regs.size(), false, false);
        return new AttendeePage(items, pagination);
    }
}
