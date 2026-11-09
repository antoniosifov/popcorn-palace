package com.antoniosifov.popcornpalace.common;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.common.web.SortFields;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortFieldsTest
{
    private static final Set<String> ALLOWED = Set.of("title", "rating");

    @Test
    void acceptsAllowedFieldsAndUnsortedRequests()
    {
        assertThatCode(() -> SortFields.requireAllowed(PageRequest.of(0, 20, Sort.by("title", "rating")), ALLOWED))
                .doesNotThrowAnyException();
        assertThatCode(() -> SortFields.requireAllowed(Pageable.unpaged(), ALLOWED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFieldOutsideTheAllowList()
    {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("title", "secret"));

        assertThatThrownBy(() -> SortFields.requireAllowed(pageable, ALLOWED))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_SORT))
                .hasMessage("Cannot sort by 'secret'. Allowed fields: rating, title");
    }
}
