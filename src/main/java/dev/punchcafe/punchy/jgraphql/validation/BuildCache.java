package dev.punchcafe.punchy.jgraphql.validation;

import dev.punchcafe.punchy.jgraphql.DataFetcherProvider;
import dev.punchcafe.punchy.jgraphql.GraphQLTypeModel;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BuildCache {

    private static final String UNSATISFIED_TYPES_MESSAGE = "GraphQLTypeModels could not be found for the following GraphQL types: %s";
    private static final String UNSATISFIED_TYPES_MESSAGE_LIST_DELIMETER = ", ";

    Map<GraphQLType, GraphQLTypeModel> modelMap;
    Map<GraphQLTypeModel, List<DataFetcherProvider>> dataFetcherMap = new HashMap<>();

    public void buildCache(GraphQLSchema schema, List<GraphQLTypeModel> models, List<DataFetcherProvider> providers) {
        this.modelMap = buildModelMap(schema, models);


    }

    private Map<GraphQLType, GraphQLTypeModel> buildModelMap(GraphQLSchema schema, List<GraphQLTypeModel> models) {
        Map<GraphQLType, GraphQLTypeModel> modelMap = new HashMap<>();
        for (GraphQLTypeModel model : models) {
            GraphQLType schemaType = schema.getType(model.getTypeName());
            if (schemaType == null) {
                throw new RuntimeException("unexpected model");
            }
            modelMap.put(schemaType, model);
        }
        List<String> missingTypes = schema.getAllTypesAsList().stream().filter(graphQLType -> modelMap.get(graphQLType) == null).map(GraphQLType::getName).collect(Collectors.toList());
        if (!missingTypes.isEmpty()) {
            String message = String.format(UNSATISFIED_TYPES_MESSAGE, String.join(UNSATISFIED_TYPES_MESSAGE_LIST_DELIMETER, missingTypes).substring(0, UNSATISFIED_TYPES_MESSAGE_LIST_DELIMETER.length()));
            throw new RuntimeException(message);
        }
        return modelMap;
    }

}
