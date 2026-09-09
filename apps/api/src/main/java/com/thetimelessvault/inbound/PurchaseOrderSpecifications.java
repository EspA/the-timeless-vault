package com.thetimelessvault.inbound;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class PurchaseOrderSpecifications {

    private PurchaseOrderSpecifications() {
    }

    record Query(
            String number,
            String supplier,
            String status,
            String total,
            String qty,
            String arrival,
            String carrier,
            String tracking
    ) {
        static Query empty() {
            return new Query(null, null, null, null, null, null, null, null);
        }
    }

    static Specification<PurchaseOrder> matching(Query query) {
        Query q = query == null ? Query.empty() : query;
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(numberMatches(cb, root, q.number()));
            if (notBlank(q.supplier())) {
                Join<PurchaseOrder, Supplier> supplier = root.join("supplier", JoinType.INNER);
                predicates.add(contains(cb, supplier.get("name"), q.supplier()));
            }
            predicates.add(enumEquals(cb, root.get("status"), q.status(), PurchaseOrderStatus.class));
            predicates.add(contains(cb, root.get("expectedArrival"), q.arrival()));
            predicates.add(totalMatches(root, cq, cb, moneyNeedle(q.total())));
            predicates.add(qtyMatches(root, cq, cb, q.qty()));
            predicates.add(carrierMatches(root, cq, cb, q.carrier()));
            predicates.add(trackingMatches(root, cq, cb, q.tracking()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate numberMatches(CriteriaBuilder cb, Root<PurchaseOrder> root, String needle) {
        if (!notBlank(needle)) {
            return cb.conjunction();
        }
        Expression<String> display = cb.concat(cb.literal("po-"), root.get("poNumber").as(String.class));
        return cb.or(
                contains(cb, root.get("poNumber"), needle),
                contains(cb, display, needle)
        );
    }

    private static Predicate totalMatches(
            Root<PurchaseOrder> root,
            CriteriaQuery<?> cq,
            CriteriaBuilder cb,
            String needle
    ) {
        if (!notBlank(needle) || cq == null) {
            return cb.conjunction();
        }
        Subquery<BigDecimal> sub = cq.subquery(BigDecimal.class);
        Root<PurchaseOrderLine> line = sub.from(PurchaseOrderLine.class);
        sub.select(cb.sum(cb.prod(line.get("unitValue"), line.get("quantity"))));
        sub.where(cb.equal(line.get("purchaseOrder"), root));
        return contains(cb, sub, needle);
    }

    private static Predicate qtyMatches(
            Root<PurchaseOrder> root,
            CriteriaQuery<?> cq,
            CriteriaBuilder cb,
            String needle
    ) {
        if (!notBlank(needle) || cq == null) {
            return cb.conjunction();
        }
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<PurchaseOrderLine> line = sub.from(PurchaseOrderLine.class);
        sub.select(cb.count(line));
        sub.where(cb.equal(line.get("purchaseOrder"), root));
        return contains(cb, sub, needle);
    }

    private static Predicate carrierMatches(
            Root<PurchaseOrder> root,
            CriteriaQuery<?> cq,
            CriteriaBuilder cb,
            String needle
    ) {
        if (!notBlank(needle) || cq == null) {
            return cb.conjunction();
        }
        ShippingCarrier parsed;
        try {
            parsed = ShippingCarrier.valueOf(needle.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return cb.disjunction();
        }
        Subquery<UUID> sub = cq.subquery(UUID.class);
        Root<PurchaseOrderTracking> tracking = sub.from(PurchaseOrderTracking.class);
        sub.select(tracking.get("id"));
        sub.where(
                cb.equal(tracking.get("purchaseOrder"), root),
                cb.equal(tracking.get("carrier"), parsed)
        );
        return cb.or(cb.equal(root.get("carrier"), parsed), cb.exists(sub));
    }

    private static Predicate trackingMatches(
            Root<PurchaseOrder> root,
            CriteriaQuery<?> cq,
            CriteriaBuilder cb,
            String needle
    ) {
        if (!notBlank(needle) || cq == null) {
            return cb.conjunction();
        }
        String pattern = containsPattern(needle);
        Subquery<UUID> sub = cq.subquery(UUID.class);
        Root<PurchaseOrderTracking> tracking = sub.from(PurchaseOrderTracking.class);
        sub.select(tracking.get("id"));
        sub.where(
                cb.equal(tracking.get("purchaseOrder"), root),
                cb.like(cb.lower(cb.coalesce(tracking.get("trackingNumber"), cb.literal(""))), pattern)
        );
        return cb.or(contains(cb, root.get("trackingNumber"), needle), cb.exists(sub));
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
