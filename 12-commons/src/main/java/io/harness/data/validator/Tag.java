package io.harness.data.validator;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;

@Documented
@Constraint(validatedBy = {TagValidator.class})
@Target({FIELD, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@ReportAsSingleViolation
public @interface Tag {
  String message() default "Invalid Tag";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}