package dev.punchcafe.punchy.jgraphql;

public interface QueryDataFetcherProvider extends DataFetcherProvider<QueryDataFetcherProvider.Query> {
    class Query implements GraphQLTypeModel {
        @Override
        public String getTypeName() {
            return "Query";
        }
    }
}
