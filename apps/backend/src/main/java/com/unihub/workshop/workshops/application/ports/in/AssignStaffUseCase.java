package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.workshops.domain.model.AssignedStaff;
import java.util.List;

public interface AssignStaffUseCase {
    List<AssignedStaff> execute(AssignStaffCommand command);
}
