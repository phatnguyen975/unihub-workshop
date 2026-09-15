package com.unihub.workshop.iam.presentation.mapper;

import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.presentation.dto.MemberDetailResponse;
import com.unihub.workshop.iam.presentation.dto.MemberSummaryResponse;
import com.unihub.workshop.iam.presentation.dto.ProvisionUserResponse;
import com.unihub.workshop.iam.presentation.dto.UpdateMemberStatusResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrganizationPresentationMapper {
    @Mapping(target = "userId", source = "id")
    ProvisionUserResponse toProvisionResponse(User user);

    @Mapping(target = "userId", source = "id")
    UpdateMemberStatusResponse toStatusResponse(User user);

    MemberDetailResponse toDetailResponse(User user);

    MemberSummaryResponse toSummaryResponse(User user);

    List<MemberSummaryResponse> toSummaryResponseList(List<User> users);
}
