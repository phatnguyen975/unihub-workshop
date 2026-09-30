package com.unihub.workshop.workshops.application.ports.in;

public interface RevokeStaffUseCase {
    void execute(RevokeStaffCommand command);
}
