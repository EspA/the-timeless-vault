package com.thetimelessvault.inventory;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.publish.ChannelListing;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class InventorySpecifications {

    private InventorySpecifications() {
    }

    record Query(
            String q,
            String sku,
            String set,
            String title,
            String created,
            String ebayPrice,
            String bricklinkPrice,
            String shopifyPrice,
            String cost,
            String stockStatus,
            String quantity,
            String condition,
            String shopify,
            String bricklink,
            String ebay,
            String sort,
            String dir
    ) {
    }

    static Specification<InventoryItem> matching(Query query) {
        return (root, cq, cb) -> {
            boolean selectingItems = cq != null && InventoryItem.class.equals(cq.getResultType());
            if (selectingItems) {
                root.fetch("catalogItem", JoinType.INNER);
            }
            Join<InventoryItem, CatalogItem> catalog = selectingItems
                    ? null
                    : root.join("catalogItem", JoinType.INNER);
            Join<InventoryItem, ChannelListing> shopify = listingJoin(root, cb, Platform.SHOPIFY);
            Join<InventoryItem, ChannelListing> bricklink = listingJoin(root, cb, Platform.BRICKLINK);
            Join<InventoryItem, ChannelListing> ebay = listingJoin(root, cb, Platform.EBAY);
            List<Predicate> predicates = new ArrayList<>();
            Expression<String> setNumber = selectingItems
                    ? root.get("catalogItem").get("setNumber")
                    : catalog.get("setNumber");
            predicates.add(contains(cb, root.get("sku"), query.sku()));
            predicates.add(contains(cb, setNumber, query.set()));
            predicates.add(contains(cb, root.get("title"), query.title()));
            predicates.add(contains(cb, root.get("createdAt"), query.created()));
            predicates.add(contains(cb, root.get("ebayPrice"), query.ebayPrice()));
            predicates.add(contains(cb, root.get("bricklinkPrice"), query.bricklinkPrice()));
            predicates.add(contains(cb, root.get("shopifyPrice"), query.shopifyPrice()));
            predicates.add(contains(cb, root.get("cost"), query.cost()));
            predicates.add(contains(cb, root.get("quantity"), query.quantity()));
            if (notBlank(query.stockStatus())) {
                predicates.add(cb.equal(root.get("stockStatus"), query.stockStatus().trim()));
            }
            if (notBlank(query.condition())) {
                predicates.add(cb.equal(root.get("condition"), query.condition().trim()));
            }
            predicates.add(listingStatus(cb, shopify, "shopifyStatus", query.shopify()));
            predicates.add(listingStatus(cb, bricklink, "bricklinkStatus", query.bricklink()));
            predicates.add(listingStatus(cb, ebay, "ebayStatus", query.ebay()));
            if (notBlank(query.q())) {
                String needle = "%" + query.q().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sku")), needle),
                        cb.like(cb.lower(setNumber), needle),
                        cb.like(cb.lower(root.get("title")), needle)
                ));
            }
            if (selectingItems && cq != null) {
                boolean asc = query.dir() == null || !"desc".equalsIgnoreCase(query.dir());
                Expression<?> order = orderBy(root, setNumber, shopify, bricklink, ebay, query.sort());
                cq.orderBy(asc ? cb.asc(order) : cb.desc(order), cb.desc(root.get("createdAt")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Expression<?> orderBy(
            Root<InventoryItem> root,
            Expression<String> setNumber,
            Join<InventoryItem, ChannelListing> shopify,
            Join<InventoryItem, ChannelListing> bricklink,
            Join<InventoryItem, ChannelListing> ebay,
            String sort
    ) {
        return switch (sort == null ? "created" : sort) {
            case "sku" -> root.get("sku");
            case "set" -> setNumber;
            case "title" -> root.get("title");
            case "ebayPrice" -> root.get("ebayPrice");
            case "bricklinkPrice" -> root.get("bricklinkPrice");
            case "shopifyPrice" -> root.get("shopifyPrice");
            case "cost" -> root.get("cost");
            case "stockStatus" -> root.get("stockStatus");
            case "quantity" -> root.get("quantity");
            case "condition" -> root.get("condition");
            case "shopify" -> shopify.get("shopifyStatus");
            case "bricklink" -> bricklink.get("bricklinkStatus");
            case "ebay" -> ebay.get("ebayStatus");
            default -> root.get("createdAt");
        };
    }

    @SuppressWarnings("unchecked")
    private static Join<InventoryItem, ChannelListing> listingJoin(
            Root<InventoryItem> root,
            CriteriaBuilder cb,
            Platform platform
    ) {
        Join<InventoryItem, ChannelListing> join = (Join<InventoryItem, ChannelListing>) (Join<?, ?>) root.join("channelListings", JoinType.LEFT);
        join.on(cb.equal(join.get("platform"), platform));
        return join;
    }

    private static Predicate listingStatus(
            CriteriaBuilder cb,
            Join<InventoryItem, ChannelListing> listing,
            String field,
            String filter
    ) {
        if (!notBlank(filter)) {
            return cb.conjunction();
        }
        if ("none".equals(filter)) {
            return cb.isNull(listing.get(field));
        }
        return cb.equal(listing.get(field), filter.trim());
    }

    private static Predicate contains(CriteriaBuilder cb, Expression<?> field, String needle) {
        if (!notBlank(needle)) {
            return cb.conjunction();
        }
        return cb.like(cb.lower(cb.coalesce(field.as(String.class), cb.literal(""))), "%" + needle.trim().toLowerCase(Locale.ROOT) + "%");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
