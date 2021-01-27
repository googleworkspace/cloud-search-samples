# Full Traversal Connector Sample

[![Open in Cloud Shell][cloudshell-badge]][cloudshell-open]

[Google Cloud Search][cloud-search] allows enterprises to index and search
for information across a wide variety of data sources. This sample illustrates
building a simple idenity connector using the [Connector SDK][sdk-guide]
to map external identity and sync group rosters.

## Set up the samples

1. [Set up a project][project-setup] for Cloud Search
1. [Create an identity][create-identity-source]
1. [Get your customer ID][get-customer-id]
1. Install [Maven][maven-install].
1. Build the project with:

   ```
   mvn clean package -DskipTests
   ```

### Configure the connector

Prior to running the sample, edit `sample-config.properties` to provide the
identity source ID, customer ID, and service account credentials. For example:

```
api.identitySourceId=1234567890abcdef
api.customerId=123abc
api.serviceAccountPrivateKeyFile=./my-connector-credentials.json
```

### Supply your identity mappings and group rosters

You must provide valid Google accounts in the CSV file to run the connector.
You do not need to map all users in the domain. A small number of users is
sufficient to run the connector.

See [`sample-users.csv`](sample-users.csv) and
[`sample-groups.csv`](sample-groups.csv) for examples of how to format
the CSV files.

Place your own mappings in `users.csv` and `groups.csv`
### Run the connector

To run the connector sync identities, run the command:

```
mvn exec:java -Dexec.mainClass=com.google.cloudsearch.samples.IdentityConnectorSample \
    -Dexec.args="-Dconfig=sample-config.properties"
```

The connector is configured to run once and exit.

[cloudshell-badge]: http://gstatic.com/cloudssh/images/open-btn.png
[cloudshell-open]: https://console.cloud.google.com/cloudshell/open?git_repo=https://github.com/googleworkspace/cloud-search-samples&page=editor&open_in_editor=identity/connector/sdk/full-sync/README.md
[cloud-search]: https://developers.google.com/cloud-search/
[google-api-java]: https://github.com/google/google-api-java-client
[sdk-guide]: https://developers.google.com/cloud-search/docs/guides/identity-connector
[project-setup]: https://developers.google.com/cloud-search/docs/guides/project-setup
[create-identity-source]: https://support.google.com/a/answer/7430822?pli=1
[maven-install]: http://maven.apache.org/install.html
[get-customer-id]: TODO_NO_DOC_TO_LINK_TO_YET
