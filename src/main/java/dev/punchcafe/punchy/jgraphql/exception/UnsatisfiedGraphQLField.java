package dev.punchcafe.punchy.jgraphql.exception;

import static dev.punchcafe.punchy.jgraphql.exception.ExceptionMessages.MISSING_GRAPHQL_FIELD_MESSAGE;

public class UnsatisfiedGraphQLField extends RuntimeException {
    public UnsatisfiedGraphQLField(String message){
        super(message);
    }
}
