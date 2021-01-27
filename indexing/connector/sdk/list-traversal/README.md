# Listing Traversal Connector Sample

[![Open in Cloud Shell][cloudshell-badge]][cloudshell-open]

[Google Cloud Search][cloud-search] allows enterprises to index and search
for information across a wide variety of data sources. This sample illustrates
building a simple indexing connector using the [Connector SDK][sdk-guide]
and queues to more efficiently index content vs. the full traversal strategy.

## Set up the samples

1. [Set up a project][project-setup] for Cloud Search
1. [Create a datasource][create-datasource]
1. Install [Maven][maven-install].
1. Build the project with:

   ```
   mvn clean package -DskipTests
   ```

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
mvn exec:java -Dexec.mainClass=com.google.cloudsearch.samples.ListTraversalSample \
    -Dexec.args="-Dconfig=sample-config.properties"
```

The connector is configured to run continuously with a full traversal every 2
minutes. At the start of each traversal a set of random mutations are applied
to the generated documents to simulate a production system.

[cloudshell-badge]: http://gstatic.com/cloudssh/images/open-btn.png
[cloudshell-open]: https://console.cloud.google.com/cloudshell/open?git_repo=https://github.com/googleworkspace/cloud-search-samples&page=editor&open_in_editor=indexing/connector/sdk/list-traversal/README.md
[cloud-search]: https://developers.google.com/cloud-search/
[google-api-java]: https://github.com/google/google-api-java-client
[sdk-guide]: https://developers.google.com/cloud-search/docs/guides/connector-overview
[project-setup]: https://developers.google.com/cloud-search/docs/guides/project-setup
[create-datasource]: https://support.google.com/a/answer/7430822?pli=1
[maven-install]: http://maven.apache.org/install.html
