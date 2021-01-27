# ACL Samples

[![Open in Cloud Shell][cloudshell-badge]][cloudshell-open]

[Google Cloud Search][cloud-search] allows enterprises to index and search
for information across a wide variety of data sources. This sample illustrates
how to create ACLs during indexing. It uses filesystem permissions and ACLs
as an example.

The sample does not index content. It only displays what the ACL for
a given object would be if indexed. The sample works with both POSIX-based and
ACL-based file permissions and will attempt to display both. Results
vary based on the filesystem.

## Set up the samples

1. [Set up a project][project-setup] for Cloud Search
1. Install [Maven][maven-install].
1. Build the project with:

   ```
   mvn clean package -DskipTests
   ```

### Run the sample

To run the sample, run the following command replacing `path_to_file` with
a valid file path:

```
mvn exec:java -Dexec.mainClass=com.google.cloudsearch.samples.FullTraversalSample \
    -Dexec.args="path_to_file"
```

The Cloud Search ACL for the file will be displayed.

[cloudshell-badge]: http://gstatic.com/cloudssh/images/open-btn.png
[cloudshell-open]: https://console.cloud.google.com/cloudshell/open?git_repo=https://github.com/googleworkspace/cloud-search-samples&page=editor&open_in_editor=indexing/connector/sdk/acls/README.md
[cloud-search]: https://developers.google.com/cloud-search/
[google-api-java]: https://github.com/google/google-api-java-client
[sdk-guide]: https://developers.google.com/cloud-search/docs/guides/connector-overview
[project-setup]: https://developers.google.com/cloud-search/docs/guides/project-setup
[maven-install]: http://maven.apache.org/install.html
