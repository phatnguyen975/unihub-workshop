package com.unihub.workshop.iam.presentation.mapper;

import com.unihub.workshop.iam.application.ports.in.AuthSessionResult;
import com.unihub.workshop.iam.application.ports.in.TokenResult;
import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.presentation.dto.LoginResponse;
import com.unihub.workshop.iam.presentation.dto.RefreshTokenResponse;
import com.unihub.workshop.iam.presentation.dto.SessionItemResponse;
import com.unihub.workshop.iam.presentation.dto.UserProfileResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthPresentationMapper {
    @Mapping(target = "user", source = "user")
    LoginResponse toLoginResponse(AuthSessionResult result);

    UserProfileResponse toProfile(User user);

    RefreshTokenResponse toRefreshTokenResponse(TokenResult result);

    SessionItemResponse toSessionItem(ActiveSession session);

    List<SessionItemResponse> toSessionItemList(List<ActiveSession> sessions);
}
