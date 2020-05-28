package dev.punchcafe.punchy.jgraphql;

public interface QueryDataFetcherProvider extends DataFetcherProvider<QueryDataFetcherProvider.Query> {
    class Query implements GraphQLType{
        @Override
        public String typeName() {
            return "Query";
        }
    }
}
