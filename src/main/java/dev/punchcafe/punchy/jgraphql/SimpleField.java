package dev.punchcafe.punchy.jgraphql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * indicates a field (or gettter or setter) is the POJO field on the {@link GraphQLTypeModel} for resolving that simple field.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SimpleField {
    String value();

    String methodName() default "";
}
