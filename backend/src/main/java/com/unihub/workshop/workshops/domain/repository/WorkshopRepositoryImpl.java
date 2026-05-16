package com.unihub.workshop.workshops.domain.repository;

import com.unihub.workshop.workshops.application.usecases.WorkshopDiscoveryCriteria;
import com.unihub.workshop.workshops.domain.model.Workshop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class WorkshopRepositoryImpl implements WorkshopRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Workshop> searchPublishedWorkshops(WorkshopDiscoveryCriteria criteria, Pageable pageable) {
        StringBuilder whereClause = new StringBuilder(" FROM workshops w WHERE w.status = 'PUBLISHED'");
        Map<String, Object> parameters = new HashMap<>();

        if (StringUtils.hasText(criteria.q())) {
            whereClause.append(" AND to_tsvector('simple', w.title || ' ' || coalesce(w.description, '')) @@ plainto_tsquery('simple', :q)");
            parameters.put("q", criteria.q().trim());
        }

        if (criteria.organizerId() != null) {
            whereClause.append(" AND w.organizer_id = :organizerId");
            parameters.put("organizerId", criteria.organizerId());
        }

        if (criteria.startDate() != null) {
            whereClause.append(" AND w.start_time >= :startDate");
            parameters.put("startDate", criteria.startDate());
        }

        if (criteria.endDate() != null) {
            whereClause.append(" AND w.end_time <= :endDate");
            parameters.put("endDate", criteria.endDate());
        }

        if (criteria.minCapacity() != null) {
            whereClause.append(" AND w.capacity_total >= :minCapacity");
            parameters.put("minCapacity", criteria.minCapacity());
        }

        String orderByClause = resolveOrderBy(pageable);

        Query contentQuery = entityManager.createNativeQuery(
                "SELECT w.*" + whereClause + orderByClause,
                Workshop.class);
        Query countQuery = entityManager.createNativeQuery("SELECT count(*)" + whereClause);

        parameters.forEach(contentQuery::setParameter);
        parameters.forEach(countQuery::setParameter);

        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Workshop> workshops = contentQuery.getResultList();
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(workshops, pageable, totalElements);
    }

    private String resolveOrderBy(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return " ORDER BY w.start_time ASC, w.id ASC";
        }

        StringBuilder orderBy = new StringBuilder(" ORDER BY ");
        boolean first = true;
        for (var order : pageable.getSort()) {
            if (!first) {
                orderBy.append(", ");
            }
            orderBy.append("w.").append(resolveColumn(order.getProperty())).append(' ')
                    .append(order.getDirection().name());
            first = false;
        }
        orderBy.append(", w.id ASC");
        return orderBy.toString();
    }

    private String resolveColumn(String property) {
        return switch (property) {
            case "id" -> "id";
            case "version" -> "version";
            case "title" -> "title";
            case "description" -> "description";
            case "startTime" -> "start_time";
            case "endTime" -> "end_time";
            case "capacityTotal" -> "capacity_total";
            case "capacityRemaining" -> "capacity_remaining";
            case "priceAmount" -> "price_amount";
            case "location" -> "location";
            case "venue" -> "venue";
            case "room" -> "room";
            case "roomLayoutKey" -> "room_layout_key";
            case "createdAt" -> "created_at";
            default -> throw new IllegalArgumentException("Unsupported sort field: " + property);
        };
    }
}
