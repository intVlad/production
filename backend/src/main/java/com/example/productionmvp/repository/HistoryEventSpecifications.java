package com.example.productionmvp.repository;

import com.example.productionmvp.model.HistoryEvent;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filters for the audit screen, built as a specification rather than one JPQL string with
 * {@code (:param IS NULL OR ...)} guards.
 *
 * <p>That guard pattern works on H2 and fails on PostgreSQL: with a null argument the driver
 * sees a bare parameter in {@code $3 IS NULL} with nothing to infer a type from and rejects the
 * statement outright ("could not determine data type of parameter"). The audit screen therefore
 * returned 500 on the actual deployment database while passing every test against the in-memory
 * one. Building only the predicates that are actually needed means a null filter contributes no
 * parameter at all, so there is nothing left to infer.
 */
final class HistoryEventSpecifications {

    private HistoryEventSpecifications() {}

    static Specification<HistoryEvent> filtered(UUID workerId, LocalDateTime since, LocalDateTime until) {
        return (root, query, cb) -> {
            // Spring Data runs this same specification a second time as a count query whenever
            // it needs a total (any page smaller than the result set), and a fetch join is
            // illegal there - it threw, and the request came back as an error. Only fetch when
            // this is the real query.
            Class<?> resultType = query == null ? null : query.getResultType();
            boolean countQuery = resultType == Long.class || resultType == long.class;

            Join<?, ?> workerJoin;
            if (countQuery) {
                workerJoin = root.join("worker", JoinType.LEFT);
            } else {
                // Left joins, not path navigation: a system-generated event has no worker, and
                // an inner join would drop those rows whenever no worker filter is applied.
                Fetch<?, ?> workerFetch = root.fetch("worker", JoinType.LEFT);
                root.fetch("task", JoinType.LEFT);
                root.fetch("productInstance", JoinType.LEFT);
                root.fetch("stage", JoinType.LEFT);
                root.fetch("operation", JoinType.LEFT);
                root.fetch("series", JoinType.LEFT);
                root.fetch("batch", JoinType.LEFT);
                // Reuse the fetch join rather than calling root.get("worker"), which would add a
                // second join for the same association.
                workerJoin = (Join<?, ?>) workerFetch;
            }

            List<Predicate> predicates = new ArrayList<>();
            if (workerId != null) {
                predicates.add(cb.equal(workerJoin.get("id"), workerId));
            }
            if (since != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), since));
            }
            if (until != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), until));
            }

            // A count query rejects an ORDER BY over a column it does not select.
            if (!countQuery && query != null) {
                query.orderBy(cb.desc(root.get("timestamp")));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
