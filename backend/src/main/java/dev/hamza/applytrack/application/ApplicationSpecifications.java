package dev.hamza.applytrack.application;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Locale;

/**
 * Composable query filters for the list endpoint. The owner filter is always applied.
 */
final class ApplicationSpecifications {

    private ApplicationSpecifications() {
    }

    static Specification<JobApplication> forFilter(Long ownerId, ApplicationFilter filter) {
        Specification<JobApplication> spec = ownedBy(ownerId);
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            spec = spec.and(statusIn(filter.statuses()));
        }
        if (StringUtils.hasText(filter.q())) {
            spec = spec.and(matchesText(filter.q()));
        }
        if (StringUtils.hasText(filter.tag())) {
            spec = spec.and(hasTag(filter.tag()));
        }
        return spec;
    }

    static Specification<JobApplication> ownedBy(Long ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    static Specification<JobApplication> statusIn(Collection<ApplicationStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    /** Case-insensitive substring match on company, role or location. */
    static Specification<JobApplication> matchesText(String q) {
        String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("company")), pattern, '\\'),
                cb.like(cb.lower(root.get("role")), pattern, '\\'),
                cb.like(cb.lower(root.get("location")), pattern, '\\'));
    }

    /**
     * Uses an EXISTS sub-query instead of a join so paging and count queries never see duplicate rows.
     */
    static Specification<JobApplication> hasTag(String tag) {
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<JobApplication> inner = sub.from(JobApplication.class);
            Join<JobApplication, String> tags = inner.join("tags");
            sub.select(inner.get("id"))
                    .where(cb.equal(inner.get("id"), root.get("id")), cb.equal(cb.lower(tags), normalized));
            return cb.exists(sub);
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
