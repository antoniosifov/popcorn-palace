package com.antoniosifov.popcornpalace.showtime;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint: {@code endTime} must be later than {@code startTime}.
 * A rule about two fields cannot be expressed with field annotations alone.
 */
@Documented
@Constraint(validatedBy = EndAfterStartValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EndAfterStart
{
    String message() default "must be after startTime";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
