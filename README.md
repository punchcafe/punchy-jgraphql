
## Configuring schema files:
add `graphqls` files as a list to the `graphql.punchy.schema-files: graphqls/student.graphqls`
**NOTE:** the file location root is the resources folder, so only list children directories, or the file itself if it's in the resources directory.

Rules:
- You must provider at least one `DataFetcherProvider` implementing class for each `@GraphQLTypeModel`, otherwise the model will not be discoverable.
- If a SimpleField is used on a field, you must also provide the methodName