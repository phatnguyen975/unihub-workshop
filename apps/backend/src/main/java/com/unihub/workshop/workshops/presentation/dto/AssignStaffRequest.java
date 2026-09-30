package com.unihub.workshop.workshops.presentation.dto;

import java.util.List;
import java.util.UUID;

public record AssignStaffRequest(List<UUID> staffUserIds) {}
