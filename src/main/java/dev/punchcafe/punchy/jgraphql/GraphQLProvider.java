package dev.punchcafe.punchy.jgraphql;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnClass(GraphQL.class)
@EnableConfigurationProperties(PunchyConfiguration.class)
public class GraphQLProvider {

    private List<DataFetcherProvider<?>> dataFetcherProviders;
    private QueryDataFetcherProvider queryDataFetcherProvider;
    private PunchyConfiguration punchyConfiguration;
    private GraphQL graphQL;

    @Bean
    public GraphQL graphQL() {
        return graphQL;
    }

    @Autowired
    public GraphQLProvider(List<DataFetcherProvider<?>> dataFetcherProviders,
                           PunchyConfiguration configuration) {
        //TODO: should also be able to grab by type? but maybe not necessary
        //TODO: add a proper error if more than one or non found.
        //TODO: add checks to make sure no-one is trying to make a query type
        //TODO: make sure there are no duplicate type names
        /**
         * Grab all {@link GraphQL} types, and create a configuration map between them
         * and their respective Query Data Fetcher Providers. Build wiring from this,
         * as it will also allow for fragmented resolvers.
         *
         *
         */
        System.out.println("listing grabbed data providers!");
        System.out.println(dataFetcherProviders);
        final var queryFetcher = dataFetcherProviders.stream().filter(df -> df instanceof QueryDataFetcherProvider).findFirst().orElseThrow();
        final var nonQuery = dataFetcherProviders.stream().filter(df -> !(df instanceof QueryDataFetcherProvider)).collect(Collectors.toList());
        System.out.println(queryFetcher);
        this.queryDataFetcherProvider = (QueryDataFetcherProvider) queryFetcher;
        this.dataFetcherProviders = nonQuery;
        this.punchyConfiguration = configuration;
    }

    @PostConstruct
    public void init() throws IOException {
        StringBuilder sb = new StringBuilder();
        if(this.punchyConfiguration == null || this.punchyConfiguration.getSchemaFiles() == null){
            throw new RuntimeException("Must provide a schema through application properties");
        }
        for(String schemaFile : punchyConfiguration.getSchemaFiles()){
            URL url = Resources.getResource(schemaFile);
            String sdl = Resources.toString(url, Charsets.UTF_8);
            sb.append(sdl);
        }
        GraphQLSchema graphQLSchema = buildSchema(sb.toString());
        this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    private GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = this.buildRuntimeWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildRuntimeWiring() {
        final RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
        builder.type(buildSpecialWiringFromInstance(this.queryDataFetcherProvider, "Query"));
        for (DataFetcherProvider<?> dataFetcherProvider : this.dataFetcherProviders) {
            builder.type(buildWiringFromInstance(dataFetcherProvider));
        }
        return builder.build();
    }

    private TypeRuntimeWiring.Builder buildWiringFromInstance(DataFetcherProvider instance) {
        System.out.println(instance.getClass().getGenericInterfaces());
        final Class type = (Class) (((ParameterizedType) instance.getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0]);
        String typeName;
        try {
            typeName = type.getMethod("typeName").invoke(null).toString();
        } catch (Exception ex) {
            typeName = type.getSimpleName();
        }
        return TypeRuntimeWiring.newTypeWiring(typeName).dataFetchers(buildDataFetchersFromInstance(instance));
    }

    private TypeRuntimeWiring.Builder buildSpecialWiringFromInstance(DataFetcherProvider instance, String specialTypeName) {
        System.out.println(instance);
        System.out.println("***");
        System.out.println(buildDataFetchersFromInstance(instance));
        return TypeRuntimeWiring.newTypeWiring(specialTypeName).dataFetchers(buildDataFetchersFromInstance(instance));
    }

    private Map<String, DataFetcher> buildDataFetchersFromInstance(DataFetcherProvider<?> instance) {
        System.out.println(instance);

        final Map<String, DataFetcher> dataFetchers = new HashMap<>();
        try {
            for (Method method : instance.getClass().getMethods()) {
                // only get annotated methods
                if (!DataFetcher.class.equals(method.getReturnType()) && !Arrays.asList(method.getReturnType().getInterfaces()).contains(DataFetcher.class)) {
                    continue;
                } else {
                    final DataFetcher<?> dataFetcher = (DataFetcher) method.invoke(instance);
                    dataFetchers.put(method.getAnnotation(ComplexField.class).value(), dataFetcher);
                }
            }
        } catch (Exception ex) {
            System.out.println("error");
            System.out.println(ex);
            System.out.println(ex.getMessage());
        }
        System.out.println(dataFetchers);
        return dataFetchers;
    }
}
