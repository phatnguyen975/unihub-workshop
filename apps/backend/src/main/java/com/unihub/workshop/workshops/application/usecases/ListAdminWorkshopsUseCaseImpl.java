package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.WorkshopSummary;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAdminWorkshopsUseCaseImpl
    implements ListAdminWorkshopsUseCase
{

    private static final Set<String> ALLOWED_SORTS = Set.of(
        "createdAt,desc",
        "createdAt,asc",
        "startsAt,asc",
        "startsAt,desc",
        "capacity,asc",
        "capacity,desc",
        "price,asc",
        "price,desc"
    );

    private final WorkshopRepository workshopRepository;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<WorkshopSummary> execute(ListAdminWorkshopsQuery query) {
        if (query == null || query.organizationId() == null) {
            throw new BadRequestException("Organization ID is required");
        }

        int pageSize = query.size() == null ? 20 : query.size();
        if (pageSize < 1 || pageSize > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }

        String priceType = null;
        if (query.priceType() != null && !query.priceType().isBlank()) {
            String normalizedPriceType = query
                .priceType()
                .trim()
                .toUpperCase(Locale.ROOT);
            if (
                !"FREE".equals(normalizedPriceType) &&
                !"PAID".equals(normalizedPriceType)
            ) {
                throw new BadRequestException(
                    "Invalid priceType: " +
                        query.priceType() +
                        ". Allowed values: FREE, PAID."
                );
            }
            priceType = normalizedPriceType;
        }

        String sort = query.sort();
        if (sort == null || sort.isBlank()) {
            sort = "createdAt,desc";
        } else {
            sort = sort.trim();
            if (!ALLOWED_SORTS.contains(sort)) {
                throw new BadRequestException(
                    "Invalid sort parameter: " +
                        sort +
                        ". Supported: " +
                        String.join(", ", ALLOWED_SORTS)
                );
            }
        }

        if (
            query.from() != null &&
            query.to() != null &&
            query.from().isAfter(query.to())
        ) {
            throw new BadRequestException(
                "'from' timestamp cannot be after 'to' timestamp"
            );
        }

        ListAdminWorkshopsQuery validatedQuery = query
            .toBuilder()
            .size(pageSize)
            .priceType(priceType)
            .sort(sort)
            .build();

        return workshopRepository.findAllByOrganizationWithFilters(
            validatedQuery
        );
    }
}
