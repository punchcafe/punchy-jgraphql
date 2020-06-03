package dev.punchcafe.punchy.jgraphql.exception;

public class UnsatisfiedGraphQLType extends RuntimeException {
    public UnsatisfiedGraphQLType(String message){
        super(message);
    }
}
