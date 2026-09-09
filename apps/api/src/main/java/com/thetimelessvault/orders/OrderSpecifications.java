package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class OrderSpecifications {

    private OrderSpecifications() {
    }

    record Query(
            String when,
            String platform,
            String item,
            String qty,
            String price,
            String shipping,
            String fee,
            String order,
            String status,
            String tracking,
            String provider
    ) {
        static Query empty() {
            return new Query(null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    static Specification<Order> matching(Query query) {
        Query q = query == null ? Query.empty() : query;
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(contains(cb, root.get("soldAt"), q.when()));
            predicates.add(enumEquals(cb, root.get("platform"), q.platform(), Platform.class));
            predicates.add(enumEquals(cb, root.get("status"), q.status(), OrderStatus.class));
            predicates.add(contains(cb, root.get("externalOrderId"), q.order()));
            predicates.add(contains(cb, root.get("shippingCost"), moneyNeedle(q.shipping())));
            predicates.add(contains(cb, root.get("platformFee"), moneyNeedle(q.fee())));
            predicates.add(itemMatches(root, cq, cb, q.item()));
            predicates.add(quantityMatches(root, cq, cb, q.qty()));
            predicates.add(priceMatches(root, cq, cb, moneyNeedle(q.price())));
            predicates.add(trackingMatches(root, cq, cb, q.tracking()));
            predicates.add(providerMatches(root, cq, cb, q.provider()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate itemMatches(Root<Order> root, CriteriaQuery<?> cq, CriteriaBuilder cb, String needle) {
        if (!notBlank(needle) || cq == null) {
            return cb.conjunction();
        }
        String pattern = containsPattern(needle);
        Subquery<UUID> sub = cq.subquery(UUID.class);
        Root<OrderLine> line = sub.from(OrderLine.class);
        sub.select(line.get("id"));
        sub.where(
                cb.equal(line.get("order"), root),
                cb.or(
                        cb.like(cb.lower(cb.coalesce(line.get("sku"), cb.literal(""))), pattern),
                        cb.like(cb.lower(cb.coalesce(line.get("setNumber"), cb.literal(""))), pattern),
                        cb.like(cb.lower(cb.coalesce(line.get("itemTitle"), cb.literal(""))), pattern)
                )
        );
        return cb.exists(sub);
    }

    private static Predicate quantityMatches(Root<Order> root, CriteriaQuery<?> cq, CriteriaBuilder cb, String needle) {
        if (!notBlank(needle) || cq == null) {
            return cb.conjunction();
        }
        Subquery<Number> sub = cq.subquery(Number.class);
        Root<OrderLine> line = sub.from(OrderLine.class);
        sub.select(cb.sum(line.get("quantity")));
        sub.where(cb.equal(line.get("order"), root));
        return contains(cb, sub, needle);
    }

    private static Predicate priceMatches(Root<Order> root, CriteriaQuery<?> cq, CriteriaBuilder cb, String needle) {
        if (!notBlank(needle) || cq == null) {
            return cb.conjunction();
        }
        Subquery<BigDecimal> sub = cq.subquery(BigDecimal.class);
        Root<OrderLine> line = sub.from(OrderLine.class);
        sub.select(cb.sum(cb.prod(line.get("unitPrice"), line.get("quantity"))));
        sub.where(cb.equal(line.get("order"), root));
        return contains(cb, sub, needle);
    }

    private static Predicate trackingMatches(Root<Order> root, CriteriaQuery<?> cq, CriteriaBuilder cb, String needle) {
        if (!notBlank(needle) || cq == null) {
            return cb.conjunction();
        }
        String pattern = containsPattern(needle);
        Subquery<UUID> sub = cq.subquery(UUID.class);
        Root<OrderTracking> tracking = sub.from(OrderTracking.class);
        sub.select(tracking.get("id"));
        sub.where(
                cb.equal(tracking.get("order"), root),
                cb.like(cb.lower(cb.coalesce(tracking.get("trackingNumber"), cb.literal(""))), pattern)
        );
        return cb.or(
                contains(cb, root.get("trackingNumber"), needle),
                cb.exists(sub)
        );
    }

    private static Predicate providerMatches(Root<Order> root, CriteriaQuery<?> cq, CriteriaBuilder cb, String needle) {
        if (!notBlank(needle) || cq == null) {
            return cb.conjunction();
        }
        String pattern = containsPattern(needle);
        Subquery<UUID> sub = cq.subquery(UUID.class);
        Root<OrderTracking> tracking = sub.from(OrderTracking.class);
        sub.select(tracking.get("id"));
        sub.where(
                cb.equal(tracking.get("order"), root),
                cb.like(cb.lower(cb.coalesce(tracking.get("carrier"), cb.literal(""))), pattern)
        );
        return cb.or(
                contains(cb, root.get("shippingProvider"), needle),
                cb.exists(sub)
        );
    }

    private static Predicate contains(CriteriaBuilder cb, Expression<?> field, String needle) {
        if (!notBlank(needle)) {
            return cb.conjunction();
        }
        return cb.like(cb.lower(cb.coalesce(field.as(String.class), cb.literal(""))), containsPattern(needle));
    }

    private static String containsPattern(String needle) {
        return "%" + needle.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static String moneyNeedle(String needle) {
        if (!notBlank(needle)) {
            return needle;
        }
        return needle.trim().replace("$", "").replace(",", "");
    }

    private static <E extends Enum<E>> Predicate enumEquals(
            CriteriaBuilder cb,
            Expression<?> field,
            String raw,
            Class<E> type
    ) {
        if (!notBlank(raw)) {
            return cb.conjunction();
        }
        try {
            return cb.equal(field, Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return cb.disjunction();
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
