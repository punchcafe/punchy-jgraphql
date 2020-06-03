package dev.punchcafe.punchy.jgraphql.exception;

public interface ExceptionMessages {
    String MISSING_GRAPHQL_FIELD_MESSAGE = "Unsatisfied dependency: no data fetcher has been provided for field: %s on GraphQL type: %s";
    String AMBIGUOUS_GRAPHQL_FIELD_MESSAGE = "Ambiguous Field: two annotated fields or methods found for field: %s on GraphQL model: %s";
}
