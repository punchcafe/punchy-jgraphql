package dev.punchcafe.punchy.jgraphql.validation;

import dev.punchcafe.punchy.jgraphql.ComplexField;
import dev.punchcafe.punchy.jgraphql.DataFetcherProvider;
import dev.punchcafe.punchy.jgraphql.GraphQLTypeModel;
import dev.punchcafe.punchy.jgraphql.SimpleField;
import dev.punchcafe.punchy.jgraphql.exception.AmbiguousGraphQLField;
import dev.punchcafe.punchy.jgraphql.exception.UnsatisfiedGraphQLField;
import dev.punchcafe.punchy.jgraphql.exception.UnsatisfiedGraphQLType;
import graphql.language.FieldDefinition;
import graphql.language.Node;
import graphql.language.TypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

import static dev.punchcafe.punchy.jgraphql.exception.ExceptionMessages.AMBIGUOUS_GRAPHQL_FIELD_MESSAGE;
import static dev.punchcafe.punchy.jgraphql.exception.ExceptionMessages.MISSING_GRAPHQL_FIELD_MESSAGE;

public class RuntimeWiringBuilder {
    Map<String, List<String>> typeNameToChildrenNames;
    Map<String, Class<?>> typeNameToTypeModel;
    // DataFetchers need to be threaded across different fetcher provider classes
    Map<Class<?>, Map<String,  DataFetcher>> modelToFieldProvers;

    public static RuntimeWiring runtimeWiring(TypeDefinitionRegistry typeRegistryFromSdl,
                                              Map<DataFetcherProvider, Class<?>> providersToModelClass,
                                              List<DataFetcherProvider<?>> allDataFetcherProviders) {

        // Handling all types defined in schema

        Map<String, List<String>> typeNameToChildrenName = new HashMap<>();
        for (TypeDefinition typeDefinition : typeRegistryFromSdl.types().values()) {
            //TODO: implement for handling other types
            List<Node> children = typeDefinition.getChildren();
            List<String> childrenTypeNames = children
                    .stream()
                    .filter(child -> child instanceof FieldDefinition)
                    .map(child -> ((FieldDefinition) child).getName())
                    .collect(Collectors.toList());
            typeNameToChildrenName.put(typeDefinition.getName(), childrenTypeNames);
        }

        // Handling model types

        Map<String, Class<?>> typeNameToTypeModel = new HashSet<>(providersToModelClass.values())
                .stream()
                .collect(Collectors.toMap(clazz -> {
                    try {
                        if(!clazz.isAnnotationPresent(GraphQLTypeModel.class)){
                            System.out.println("Missing an annotation for class");
                            return null;
                        }
                        return (String) clazz.getAnnotation(GraphQLTypeModel.class).typeName();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }, clazz -> clazz));

        // The third one

        Map<Class<?>, Map<String, DataFetcher>> modelToFieldProvider = new HashMap<>();

        for (DataFetcherProvider provider : allDataFetcherProviders) {
            //TODO: make this safe for multiple implementations
            // DO this higher up since we can't grab all models (since they aren't components)
            final Class<?> associatedModelClass = providersToModelClass.get(provider);
            List<Method> annotatedMethods = Arrays.stream(provider.getClass().getMethods()).filter(method -> method.isAnnotationPresent(ComplexField.class)).collect(Collectors.toList());
            Map<String, DataFetcher> fragment = new HashMap<>();
            for (Method method : annotatedMethods) {
                if (!DataFetcher.class.equals(method.getReturnType()) && !Arrays.asList(method.getReturnType().getInterfaces()).contains(DataFetcher.class)) {
                    throw new RuntimeException("Complex field annotation on method which doesn't return a data fetcher");
                }
                DataFetcher fetcher;
                try {
                    fetcher = (DataFetcher) method.invoke(provider);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException("Complex field annotation on method which doesn't return a data fetcher");
                }
                fragment.put(method.getAnnotation(ComplexField.class).value(), fetcher);
            }
            // Add to all pre-existing, or start new on if not found
            Map<String, DataFetcher> preExistingMap = modelToFieldProvider.get(associatedModelClass);
            if (preExistingMap == null) {
                modelToFieldProvider.put(associatedModelClass, fragment);
            } else {
                preExistingMap.putAll(fragment);
            }
        }

        System.out.println(typeNameToChildrenName);
        System.out.println(typeNameToTypeModel);
        System.out.println(modelToFieldProvider);

        return new RuntimeWiringBuilder(typeNameToChildrenName, typeNameToTypeModel, modelToFieldProvider).buildRuntimeWiring();
    }

    /**
     * @param typeNameToChildrenNames a map of all type names registerd in the {@link graphql.schema.GraphQLSchema},
     *                                and a list of the names of all corresponding fields.
     * @param typeNameToTypeModel
     * @param modelToFieldProvers     a nested map mapping an implementation of {@link GraphQLTypeModel} to a map of all
     *                                {@link DataFetcher} identified by the {@link dev.punchcafe.punchy.jgraphql.ComplexField}
     *                                annotation on all {@link dev.punchcafe.punchy.jgraphql.DataFetcherProvider} paramterised by that
     *                                that {@link GraphQLTypeModel}.
     */
    private RuntimeWiringBuilder(Map<String, List<String>> typeNameToChildrenNames,
                                 Map<String, Class<?>> typeNameToTypeModel,
                                 Map<Class<?>, Map<String, DataFetcher>> modelToFieldProvers) {
        this.typeNameToChildrenNames = typeNameToChildrenNames;
        this.typeNameToTypeModel = typeNameToTypeModel;
        this.modelToFieldProvers = modelToFieldProvers;
    }

    private RuntimeWiring buildRuntimeWiring() {
        RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
        System.out.println(String.format("typeNameToChildrenNames: %s", typeNameToChildrenNames));
        for (String typeName : typeNameToChildrenNames.keySet()) {
            runtimeWiringBuilder.type(buildTypeRuntimeWiringFromTypeName(typeName));
        }
        return runtimeWiringBuilder.build();
    }

    private TypeRuntimeWiring buildTypeRuntimeWiringFromTypeName(String typeName) {
        Class<?> modelClass = typeNameToTypeModel.get(typeName);
        if (modelClass == null) {
            throw new UnsatisfiedGraphQLType(String.format("No model found for GraphQL type: %s, did you forget to annotate it, or add a data fetcher component?", typeName));
        }
        Map<String, DataFetcher> fieldProviders = modelToFieldProvers.get(modelClass);

        Map<String, DataFetcher> resultantMap = new HashMap<>();

        //Extract to stream method
        for (String field : typeNameToChildrenNames.get(typeName)) {
            DataFetcher provider = fieldProviders.get(field);
            if (provider == null) {
                final Method resolvingMethodOnModelClass = getSimpleFieldProvider(modelClass, field);
                if (resolvingMethodOnModelClass == null) {
                    throw new UnsatisfiedGraphQLField(String.format(MISSING_GRAPHQL_FIELD_MESSAGE, field, typeName));
                }
                provider = buildDataFetcherFromMethod(resolvingMethodOnModelClass);
            }
            resultantMap.put(field, provider);
        }
        System.out.println(String.format("Field providers for %s: %s ", typeName, resultantMap));
        return TypeRuntimeWiring.newTypeWiring(typeName).dataFetchers(resultantMap).build();
    }

    private DataFetcher<?> buildDataFetcherFromMethod(Method method) {
        return (dfe) -> method.invoke(dfe.getSource());
    }

    /**
     * Handles finding the zero-args {@link Method} for getting the value for the simple field.
     * In the case of a method annotated with {@link SimpleField}, it simply returns the method.
     * In the case of an annotated field, it will look for the method with the name specified by
     * {@link SimpleField#methodName()}.
     *
     * @param clazz
     * @param fieldName
     * @return
     */
    private Method getSimpleFieldProvider(Class<?> clazz, String fieldName) {
        System.out.println(String.format("getting simple field providers for field %s in class %s", fieldName, clazz));
        final List<Method> fieldProviderMethods = Arrays.stream(clazz.getMethods()).filter(method -> method.isAnnotationPresent(SimpleField.class)).collect(Collectors.toList());
        final List<Field> fieldProviderFields = Arrays.stream(clazz.getDeclaredFields()).filter(field -> field.isAnnotationPresent(SimpleField.class)).collect(Collectors.toList());

        if (fieldProviderFields.size() == 0 && fieldProviderMethods.size() == 0) {
            System.out.println("none were found");
            return null;
        }

        final boolean matchingFieldsAndMethods = fieldProviderFields.size() > 0 && fieldProviderMethods.size() > 0;
        final boolean moreThanOneFieldFound = fieldProviderFields.size() > 1;
        final boolean moreThanOneMethodFound = fieldProviderMethods.size() > 1;
        if (matchingFieldsAndMethods || moreThanOneFieldFound || moreThanOneMethodFound) {
            throw new AmbiguousGraphQLField(String.format(AMBIGUOUS_GRAPHQL_FIELD_MESSAGE, fieldName, clazz.getSimpleName()));
        }

        if (!fieldProviderMethods.isEmpty()) {
            // simple field is resolved from the getter method at index 0
            return (fieldProviderMethods.get(0));
        } else {
            // simple field must be on the model field
            // Rules: method must be on class, method must have no args.
            final String methodName = fieldProviderFields.get(0).getAnnotation(SimpleField.class).methodName();
            if("".equals(methodName)){
                throw new RuntimeException("Please provide a method name when defining a SimpleField");
            }
            //TODO: add helpful message here if the person forgets on a field
            try {
                return clazz.getMethod(methodName);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("could not find method: %s on class %s");
            }
        }
    }
}
