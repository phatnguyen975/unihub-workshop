package com.unihub.workshop.iam.presentation.mapper;

import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.presentation.dto.UserProfileResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserPresentationMapper {
    UserProfileResponse toProfileResponse(User user);
}
