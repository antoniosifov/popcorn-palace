package com.antoniosifov.popcornpalace.common.web;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Restricts the {@code sort} query parameter to an explicit list of fields.
 * Without it, a client could sort by any entity property, including ones the
 * API never exposes, and an unknown name would surface as a server error.
 */
public final class SortFields
{
    private SortFields()
    {
    }

    public static void requireAllowed(Pageable pageable, Set<String> allowedFields)
    {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedFields.contains(order.getProperty())) {
                throw ApiException.badRequest(ErrorCode.INVALID_SORT,
                        "Cannot sort by '%s'. Allowed fields: %s"
                                .formatted(order.getProperty(), String.join(", ", new TreeSet<>(allowedFields))));
            }
        }
    }
}
