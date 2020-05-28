package dev.punchcafe.punchy.jgraphql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the method returns a {@link graphql.schema.DataFetcher} used to resolve a
 * complex GraphQL field, specified by the value assigned to the annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ComplexField {
    String value();

    interface QueryDataFetcherProvider extends DataFetcherProvider<QueryDataFetcherProvider.Query> {
        class Query implements GraphQLType{
            @Override
            public String typeName() {
                return "Query";
            }
        }
    }
}
