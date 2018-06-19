# Updating schemas

<a href="https://console.cloud.google.com/cloudshell/open?git_repo=https://github.com/gsuitedevs/cloudsearch-samples&page=editor&open_in_editor=indexing/schema/README.md">
<img alt="Open in Cloud Shell" src ="http://gstatic.com/cloudssh/images/open-btn.png"></a>

[Google Cloud Search][cloud-search] allows enterprises to index and search
for information across a wide variety of data sources. These sample
Java applications demonstrate how to [manage datasource schemas][schema-guide]
using the [Google APIs Client Library for Java][google-api-java].

[cloud-search]: https://developers.google.com/cloud-search/
[google-api-java]: https://github.com/google/google-api-java-client
[schema-guide]: https://developers.google.com/cloud-search/docs/guides/schema-guide

## Set up the samples

1. [Set up a project](project-setup) for Cloud Search
1. [Create a datasource](create-datasource)
1. Install [Maven](http://maven.apache.org/).
1. [Set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable][set-credentials] 
1. Build the project with:

   ```
   mvn clean package -DskipTests
   ```

[project-setup]: https://developers.google.com/cloud-search/docs/guides/project-setup
[create-datasource]: https://support.google.com/a/answer/7430822?pli=1
[set-credentials]: https://cloud.google.com/docs/authentication/getting-started

### Updating a schema

```
mvn exec:java -Dexec.mainClass=com.google.cloudsearch.samples.UpdateSchemaSample \
    -Dexec.args="update --datasource my-datasource-id"
```

The schema file argument is optional. If omitted, the included sample
`schema.json` file is used.

### Retrieve a schema

```
mvn exec:java -Dexec.mainClass=com.google.cloudsearch.samples.GetSchemaSample \
    -Dexec.args="get --datasource my-datasource-id"
```

### Delete a schema

```
mvn exec:java -Dexec.mainClass=com.google.cloudsearch.samples.DeleteSchemaSample \
    -Dexec.args="delete --datasource my-datasource-id"
```