# Full Traversal Connector Sample

<a href="https://console.cloud.google.com/cloudshell/open?git_repo=https://github.com/gsuitedevs/cloudsearch-samples&page=editor&open_in_editor=indexing/schema/README.md">
<img alt="Open in Cloud Shell" src ="http://gstatic.com/cloudssh/images/open-btn.png"></a>

[Google Cloud Search][cloud-search] allows enterprises to index and search
for information across a wide variety of data sources. This sample illustrates
building a simple indexing connector using the [Connector SDK][sdk-guide].

[cloud-search]: https://developers.google.com/cloud-search/
[google-api-java]: https://github.com/google/google-api-java-client
[schema-guide]: https://developers.google.com/cloud-search/docs/guides/schema-guide
[sdk-guide]: https://developers.google.com/cloud-search/docs/guides/connector-overview

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

### Configure the connector

Prior to running the sample, edit `sample-config.properties` to provide the
datasource ID and service account credentials. For example:

```
api.sourceId=1234567890abcdef
api.serviceAccountPrivateKeyFile=./my-connector-credentials.json
```

### Run the connector

To run the connector and index the documents, run the command:

```
mvn exec:java -Dexec.mainClass=com.google.cloudsearch.samples.FullTraversalSample \
    -Dexec.args="-Dconfig=sample-config.properties"
```

The connector is configured to run once and exit.

