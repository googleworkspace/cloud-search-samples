# Cloud identity samples

[![Open in Cloud Shell][cloudshell-badge]][cloudshell-open]

[Google Cloud Search][cloud-search] allows enterprises to index and search
for information across a wide variety of data sources. These sample
Java applications demonstrate how to [manage identities][identity-guide]
using the [Google APIs Client Library for Java][google-api-java].

## Set up the samples

1. [Set up a project][project-setup] for Cloud Search
1. [Create an identity source][create-identity-source]
1. Install [Maven][maven-install].
1. [Set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable][set-credentials]
1. Build the project with:

   ```
   mvn clean package -DskipTests
   ```

### Run the sample

To list available actions for the sample, run the command:

```
mvn exec:java -Dexec.mainClass=com.google.cloudsearch.samples.Main
```


For example, to create a group named *test* namespaced by the identity source
*4976a129fd88bcb658edd7013e800770*, run


```
mvn exec:java -Dexec.mainClass=com.google.cloudsearch.samples.Main \
    -Dexec.args=" create-group --id-source 4976a129fd88bcb658edd7013e800770 --group-id test"
```

[cloudshell-badge]: http://gstatic.com/cloudssh/images/open-btn.png
[cloudshell-open]: https://console.cloud.google.com/cloudshell/open?git_repo=https://github.com/googleworkspace/cloud-search-samples&page=editor&open_in_editor=identity/rest/cloud-identity/README.md
[cloud-search]: https://developers.google.com/cloud-search/
[google-api-java]: https://github.com/google/google-api-java-client
[project-setup]: https://developers.google.com/cloud-search/docs/guides/project-setup
[create-datasource]: https://support.google.com/a/answer/7430822?pli=1
[maven-install]: http://maven.apache.org/install.html
[set-credentials]: https://cloud.google.com/docs/authentication/getting-started
[identity-guide]: https://developers.google.com/cloud-search/docs/guides/identity-mapping
[create-identity-source]: https://support.google.com/a/answer/7430822?pli=1
