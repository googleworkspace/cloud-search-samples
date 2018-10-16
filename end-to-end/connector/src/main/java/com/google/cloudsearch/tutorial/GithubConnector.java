/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloudsearch.tutorial;

import com.google.enterprise.cloudsearch.sdk.indexing.IndexingApplication;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingConnector;
import com.google.enterprise.cloudsearch.sdk.indexing.template.ListingConnector;
import com.google.enterprise.cloudsearch.sdk.indexing.template.Repository;

/**
 * A sample connector using the Cloud Search SDK.
 *
 * <p>This is a sample connector for indexing GitHub repositories
 * that takes advantage of the Cloud Search SDK. It is part of an end-to-end
 * tutorial for building a custom search solution.
 *
 * <p>You must provide a configuration file for the connector. This
 * configuration file (for example: sample-config.properties) is supplied to
 * the connector via a command line argument:
 *
 * <pre>java com.google.cloudsearch.tutorial.GithubConnector \
 * -Dconfig=sample-config.properties
 * </pre>
 *
 * <p>Sample properties file:
 *
 * <pre>
 * # Required properties for accessing data source
 * # (These values are created by the admin before running the connector)
 * api.sourceId=1234567890abcdef
 *
 * # Path to service account credentials
 * api.serviceAccountPrivateKeyFile=./PrivateKey.json
 *
 * # All items public by default
 * defaultAcl.mode=FALLBACK
 * defaultAcl.public=true
 *
 * # Path to the schema file, used by the SchemaTool utility
 * github.schema=schema.json
 *
 * # List of organizations or repositories to index. May be in the form
 * # '<orgname>' to index all repositories in an or, or '<orgname/repo>'
 * # to index a specific repository. For multiple values, separate
 * # values with commas.
 * github.repos=gsuitedevs/md2googleslides
 *
 * # Login name of the user to authorize as when using the GitHub API
 * github.user=sqrrrl
 *
 * # Personal access token for GitHub. See https://github.com/settings/tokens
 * # for more information.
 * github.token=abc123
 * </pre>
 */
public class GithubConnector {
  // [START cloud_search_tutorial_main]
  /**
   * Main entry point for the connector. Creates and starts an indexing
   * application using the {@code ListingConnector} template and the sample's
   * custom {@code Repository} implementation.
   *
   * @param args program command line arguments
   * @throws InterruptedException thrown if an abort is issued during initialization
   */
  public static void main(String[] args) throws InterruptedException {
    Repository repository = new GithubRepository();
    IndexingConnector connector = new ListingConnector(repository);
    IndexingApplication application = new IndexingApplication.Builder(connector, args)
        .build();
    application.start();
  }
  // [END cloud_search_tutorial_main]
}
