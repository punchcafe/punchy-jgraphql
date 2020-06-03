package dev.punchcafe.punchy.jgraphql.exception;

public class AmbiguousGraphQLField extends RuntimeException {
    public AmbiguousGraphQLField(String message){
        super(message);
    }
}
