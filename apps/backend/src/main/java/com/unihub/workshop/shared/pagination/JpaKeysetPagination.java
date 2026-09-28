package com.unihub.workshop.shared.pagination;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Universal JPA Criteria helper for Keyset (seek-based) pagination with NULLS LAST semantics
 * and deterministic multi-column tie-breaking.
 */
public final class JpaKeysetPagination {

    private JpaKeysetPagination() {}

    /**
     * Slice of items loaded for the current page and an indicator if more items follow.
     *
     * @param <E> entity type
     */
    public record KeysetSlice<E>(List<E> items, boolean hasMore) {}

    /**
     * Applies deterministic multi-column ordering to {@code cq}:
     * <ol>
     *   <li>Primary sort path with {@link Nulls#LAST} (so null values always appear at the end).</li>
     *   <li>Secondary sort path (always {@code DESC}, e.g. {@code createdAt DESC}, ensuring newer records appear first when primary is tied or null).</li>
     *   <li>Unique row identifier tie-breaker (always {@code id DESC}) guaranteeing deterministic pagination.</li>
     * </ol>
     *
     * @param cb criteria builder
     * @param cq criteria query
     * @param primaryPath path to primary sorted attribute (e.g. startsAt, price, capacity)
     * @param isDesc true if primary sort is descending, false if ascending
     * @param secondaryPath path to secondary tie-breaker attribute (e.g. createdAt)
     * @param idPath path to unique row identifier (id)
     */
    public static <E, P, S> void applyOrders(
        CriteriaBuilder cb,
        CriteriaQuery<E> cq,
        Path<P> primaryPath,
        boolean isDesc,
        Path<S> secondaryPath,
        Path<UUID> idPath
    ) {
        List<Order> orders = new ArrayList<>();
        if (primaryPath != null && !primaryPath.equals(secondaryPath)) {
            if (isDesc) {
                orders.add(cb.desc(primaryPath, Nulls.LAST));
            } else {
                orders.add(cb.asc(primaryPath, Nulls.LAST));
            }
        }
        if (secondaryPath != null) {
            orders.add(cb.desc(secondaryPath));
        }
        if (idPath != null) {
            orders.add(cb.desc(idPath));
        }
        cq.orderBy(orders);
    }

    /**
     * Builds the keyset predicate that seeks past the given cursor values with strict NULLS LAST semantics.
     *
     * @param cb criteria builder
     * @param primaryPath primary sorted field path
     * @param cursorPrimaryVal primary value from previous page cursor (can be null)
     * @param isDesc true for DESC, false for ASC
     * @param secondaryPath secondary tie-breaker field path (e.g. createdAt)
     * @param cursorSecondaryVal secondary value from previous page cursor (non-null)
     * @param idPath id field path
     * @param cursorId id from previous page cursor
     * @return the seek predicate to add to the WHERE clause, or null if cursorId is null
     */
    public static <
        P extends Comparable<? super P>,
        S extends Comparable<? super S>
    > Predicate buildKeysetPredicate(
        CriteriaBuilder cb,
        Path<P> primaryPath,
        P cursorPrimaryVal,
        boolean isDesc,
        Path<S> secondaryPath,
        S cursorSecondaryVal,
        Path<UUID> idPath,
        UUID cursorId
    ) {
        if (cursorId == null) {
            return null;
        }

        // Secondary tie-breaker: (secondary < cursorSecondaryVal) OR (secondary = cursorSecondaryVal AND id < cursorId)
        Predicate secondaryTieBreaker;
        if (secondaryPath != null && cursorSecondaryVal != null) {
            secondaryTieBreaker = cb.or(
                cb.lessThan(secondaryPath, cursorSecondaryVal),
                cb.and(
                    cb.equal(secondaryPath, cursorSecondaryVal),
                    cb.lessThan(idPath, cursorId)
                )
            );
        } else {
            secondaryTieBreaker = cb.lessThan(idPath, cursorId);
        }

        // When primary path is the same as secondary path (e.g. createdAt):
        if (primaryPath == null || primaryPath.equals(secondaryPath)) {
            if (isDesc) {
                return cb.or(
                    cb.lessThan(secondaryPath, cursorSecondaryVal),
                    cb.and(
                        cb.equal(secondaryPath, cursorSecondaryVal),
                        cb.lessThan(idPath, cursorId)
                    )
                );
            } else {
                return cb.or(
                    cb.greaterThan(secondaryPath, cursorSecondaryVal),
                    cb.and(
                        cb.equal(secondaryPath, cursorSecondaryVal),
                        cb.greaterThan(idPath, cursorId)
                    )
                );
            }
        }

        // When primary path is different (e.g. startsAt, price, capacity):
        if (cursorPrimaryVal != null) {
            Predicate compareBranch = isDesc
                ? cb.lessThan(primaryPath, cursorPrimaryVal)
                : cb.greaterThan(primaryPath, cursorPrimaryVal);

            return cb.or(
                compareBranch,
                cb.and(
                    cb.equal(primaryPath, cursorPrimaryVal),
                    secondaryTieBreaker
                ),
                cb.isNull(primaryPath) // NULLS LAST: null rows come after non-null rows
            );
        } else {
            // The cursor item itself had primary value == null:
            // Since nulls are placed last, all remaining matching items must also have primary value == null
            return cb.and(cb.isNull(primaryPath), secondaryTieBreaker);
        }
    }

    /**
     * Executes the query with limit = {@code pageSize + 1} to detect if more rows exist without running COUNT(*),
     * and returns the sliced results.
     *
     * @param em entity manager
     * @param cq criteria query
     * @param pageSize requested page window size
     * @param <E> entity type
     * @return {@link KeysetSlice} containing page items and hasMore flag
     */
    public static <E> KeysetSlice<E> execute(
        EntityManager em,
        CriteriaQuery<E> cq,
        int pageSize
    ) {
        List<E> results = em
            .createQuery(cq)
            .setMaxResults(pageSize + 1)
            .getResultList();

        boolean hasMore = results.size() > pageSize;
        List<E> items = hasMore ? results.subList(0, pageSize) : results;
        return new KeysetSlice<>(items, hasMore);
    }
}
