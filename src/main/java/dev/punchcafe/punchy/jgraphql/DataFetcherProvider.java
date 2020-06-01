package dev.punchcafe.punchy.jgraphql;

import graphql.schema.DataFetchingEnvironment;

/**
 * Indicates the type provides data fetchers for parameter type, T.
 * Specific types must be assigned a {@link ComplexField} annotation.
 *
 * @param <T>
 */
public interface DataFetcherProvider<T extends GraphQLTypeModel> {

    default T getSource(DataFetchingEnvironment dfe){
        return (T) dfe.getSource();
    }
}
