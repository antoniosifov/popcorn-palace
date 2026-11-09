package com.antoniosifov.popcornpalace.showtime;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EndAfterStartValidator implements ConstraintValidator<EndAfterStart, ShowtimeRequest>
{
    @Override
    public boolean isValid(ShowtimeRequest request, ConstraintValidatorContext context)
    {
        // Missing values are reported by @NotNull on the fields themselves.
        if (request == null || request.startTime() == null || request.endTime() == null) {
            return true;
        }
        if (request.endTime().isAfter(request.startTime())) {
            return true;
        }
        // Report the problem on the endTime field instead of on the whole object,
        // so it appears in the response like any other field error.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endTime")
                .addConstraintViolation();
        return false;
    }
}
