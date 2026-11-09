package com.antoniosifov.popcornpalace.showtime;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test of the request's validation rules: no Spring context, no database. */
class ShowtimeRequestValidationTest
{
    private static final Instant SIX_PM = Instant.parse("2030-01-15T18:00:00Z");
    private static final Instant NINE_PM = Instant.parse("2030-01-15T21:00:00Z");

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator()
    {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory()
    {
        factory.close();
    }

    @Test
    void acceptsEndAfterStart()
    {
        assertThat(validator.validate(request(SIX_PM, NINE_PM))).isEmpty();
    }

    @Test
    void rejectsEndBeforeStartOnTheEndTimeField()
    {
        Set<ConstraintViolation<ShowtimeRequest>> violations = validator.validate(request(NINE_PM, SIX_PM));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath()).hasToString("endTime");
            assertThat(violation.getMessage()).isEqualTo("must be after startTime");
        });
    }

    @Test
    void rejectsZeroLengthShowtime()
    {
        assertThat(validator.validate(request(SIX_PM, SIX_PM)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("endTime");
    }

    @Test
    void reportsMissingTimesOnceAsRequired()
    {
        assertThat(validator.validate(request(null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("startTime", "endTime");
    }

    @Test
    void normalizesTheaterName()
    {
        assertThat(request(SIX_PM, NINE_PM).theater()).isEqualTo("Hall 1");
    }

    private static ShowtimeRequest request(Instant startTime, Instant endTime)
    {
        return new ShowtimeRequest(1L, "  Hall   1 ", startTime, endTime, new BigDecimal("14.50"));
    }
}
