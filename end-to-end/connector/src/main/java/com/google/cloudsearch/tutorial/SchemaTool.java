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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.cloudsearch.v1.CloudSearch;
import com.google.api.services.cloudsearch.v1.model.Operation;
import com.google.api.services.cloudsearch.v1.model.Schema;
import com.google.api.services.cloudsearch.v1.model.Status;
import com.google.api.services.cloudsearch.v1.model.UpdateSchemaRequest;
import com.google.enterprise.cloudsearch.sdk.CredentialFactory;
import com.google.enterprise.cloudsearch.sdk.LocalFileCredentialFactory;
import com.google.enterprise.cloudsearch.sdk.config.ConfigValue;
import com.google.enterprise.cloudsearch.sdk.config.Configuration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Collections;

/**
 * Utility for updating the schema for a data source. Uses
 * the Connector SDK for configuration.
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
 * # Path to the schema file, used by the SchemaTool utility
 * github.schema=schema.json
 * </pre>
 */
public class SchemaTool {

  public static final int OPERATION_POLL_INTERVAL = 3 * 1000;

  /**
   * Main entry point for the schema tool.
   */
  public static void main(String[] argv) throws Exception {
    Configuration.initConfig(argv);

    ConfigValue<String> sourceId = Configuration.getString("api.sourceId", null);
    ConfigValue<String> localSchema = Configuration.getString("github.schema", null);

    if (sourceId.get() == null) {
      throw new IllegalArgumentException("Missing api.sourceId value in configuration");
    }
    if (localSchema.get() == null) {
      throw new IllegalArgumentException("Missing github.schema value in configuration");
    }
    updateSchema(sourceId.get(), localSchema.get());
  }

  /**
   * Builds the CloudSearch service using the credentials as configured in the SDK.
   *
   * @return CloudSearch instance
   * @throws Exception if unable to read credentials
   */
  static CloudSearch buildAuthorizedClient() throws Exception {
    CredentialFactory credentialFactory = LocalFileCredentialFactory.fromConfiguration();
    GoogleCredential credential = credentialFactory.getCredential(
        Collections.singletonList("https://www.googleapis.com/auth/cloud_search"));

    // Build the cloud search client
    return new CloudSearch.Builder(
        Utils.getDefaultTransport(),
        Utils.getDefaultJsonFactory(),
        credential)
        .setApplicationName("Sample connector for GitHub")
        .build();
  }

  /**
   * Updates the schema for a datasource.
   *
   * @param dataSourceId   Unique ID of the datasource.
   * @param schemaFilePath path to JSON file containing the schema
   */
  // [START cloud_search_github_tutorial_update_schema]
  static void updateSchema(String dataSourceId, String schemaFilePath) throws Exception {
    CloudSearch cloudSearch = buildAuthorizedClient();

    Schema schema;
    try (BufferedReader br = new BufferedReader(new FileReader(schemaFilePath))) {
      schema = cloudSearch.getObjectParser().parseAndClose(br, Schema.class);
    }
    UpdateSchemaRequest updateSchemaRequest = new UpdateSchemaRequest()
        .setSchema(schema);
    String resourceName = String.format("datasources/%s", dataSourceId);
    Operation operation = cloudSearch.indexing().datasources()
        .updateSchema(resourceName, updateSchemaRequest)
        .execute();

    // Wait for the operation to complete.
    while (operation.getDone() == null || operation.getDone() == false) {
      // Wait before polling again
      Thread.sleep(OPERATION_POLL_INTERVAL);
      System.out.printf("Fetching operation: %s\n", operation.getName());
      operation = cloudSearch.operations().get(operation.getName()).execute();
    }

    // Operation is complete, check result
    Status error = operation.getError();
    if (error != null) {
      System.err.printf("Error updating schema: %s\n", error.getMessage());
    } else {
      System.out.println("Schema updated.");
    }
  }
  // [END cloud_search_github_tutorial_update_schema]
}
