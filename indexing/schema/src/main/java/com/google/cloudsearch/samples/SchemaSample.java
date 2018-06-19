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

package com.google.cloudsearch.samples;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
// [START imports]
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.cloudsearch.v1beta1.CloudSearch;
import com.google.api.services.cloudsearch.v1beta1.model.Schema;
import com.google.api.services.cloudsearch.v1beta1.model.UpdateSchemaRequest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
// [END imports]

/**
 * Sample demonstrating how to update the schema for a datasource. Reads
 * the schema from a JSON file.
 */
public class SchemaSample {

  /**
   * Command line arguments used by the sample
   */
  static class Arguments {
    enum Action {
      delete,
      get,
      update
    }

    @Parameter(names = "--datasource", required = true, description = "ID of the datasource")
    String dataSourceId;

    @Parameter(names = "--schema", description = "Path to schema file")
    String schemaFile = "schema.json";

    @Parameter(required = true, description = "Action to perform. Either update, get, delete")
    String action;
  }

  /**
   * Main entry point for the sample.
   *
   * Sample usage:
   *     # Delete a schema
   *     java com.google.cloudsearch.samples.SchemaSample --datasource my-datasource-id delete
   *
   *     # Retrieve a schema
   *     java com.google.cloudsearch.samples.SchemaSample --datasource my-datasource-id get
   *
   *     # Update a schema
   *     java com.google.cloudsearch.samples.SchemaSample --datasource my-datasource-id --schema my-schema.json update
   *
   * @param argv Command line args
   */
  public static void main(String[] argv) {
    Arguments arguments = new Arguments();
    JCommander.newBuilder()
        .addObject(arguments)
        .build()
        .parse(argv);

    SchemaSample sample = new SchemaSample();
    switch (Arguments.Action.valueOf(arguments.action.toLowerCase())) {
      case update:
        sample.updateSchema(arguments.dataSourceId, arguments.schemaFile);
        break;
      case get:
        sample.getSchema(arguments.dataSourceId);
        break;
      case delete:
        sample.deleteSchema(arguments.dataSourceId);
        break;
    }
  }

  /**
   * Builds and initializes the client with service account credentials.
   * @return CloudSearch instance
   * @throws IOException
   */
  private CloudSearch buildAuthorizedClient() throws IOException {
    // [START build_client]
    // Get the service account credentials based on the GOOGLE_APPLICATION_CREDENTIALS
    // environment variable
    GoogleCredential credential = GoogleCredential.getApplicationDefault(
        Utils.getDefaultTransport(),
        Utils.getDefaultJsonFactory());
    // Ensure credentials have the correct scope
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(Arrays.asList(
          "https://www.googleapis.com/auth/cloud_search"
      ));
    }
    // Build the cloud search client
    return new CloudSearch.Builder(
        Utils.getDefaultTransport(),
        Utils.getDefaultJsonFactory(),
        credential)
        .setApplicationName("Cloud Search Samples")
        .build();
    // [START end_client]
  }

  /**
   * Updates the schema for a datasource.
   *
   * @param dataSourceId   Unique ID of the datasource.
   * @param schemaFilePath path to JSON file containing the schema
   */
  void updateSchema(String dataSourceId, String schemaFilePath) {
    // [START update_schema]
    try {
      CloudSearch cloudSearch = buildAuthorizedClient();
      Schema schema;
      try (BufferedReader br = new BufferedReader(new FileReader(schemaFilePath))) {
        schema = cloudSearch.getObjectParser().parseAndClose(br, Schema.class);
      }
      UpdateSchemaRequest updateSchemaRequest = new UpdateSchemaRequest()
          .setSchema(schema);
      String resourceName = String.format("datasources/%s", dataSourceId);
      cloudSearch.indexing().datasources().updateSchema(resourceName, updateSchemaRequest).execute();
      System.out.println("Schema updated.");
    } catch (GoogleJsonResponseException e) {
      System.err.println("Unable to delete schema: " + e.getDetails());
    } catch (IOException e) {
      System.err.println("Unable to delete schema: " + e.getMessage());
    }
    // [END update_schema]
  }



  /**
   * Retrieves the schema for a datasource.
   *
   * @param dataSourceId Unique ID of the datasource.
   */
  private void getSchema(String dataSourceId) {
    // [START get_schema]
    try {
      CloudSearch cloudSearch = buildAuthorizedClient();
      String resourceName = String.format("datasources/%s", dataSourceId);
      Schema schema = cloudSearch.indexing().datasources().getSchema(resourceName).execute();
      System.out.println(schema.toPrettyString());
    } catch (GoogleJsonResponseException e) {
      System.err.println("Unable to delete schema: " + e.getDetails());
    } catch (IOException e) {
      System.err.println("Unable to delete schema: " + e.getMessage());
    }
    // [END get_schema]
  }

  /**
   * Deletes the schema for a datasource.
   *
   * @param dataSourceId Unique ID of the datasource.
   */
  private void deleteSchema(String dataSourceId) {
    // [START delete_schema]
    try {
      CloudSearch cloudSearch = buildAuthorizedClient();
      String resourceName = String.format("datasources/%s", dataSourceId);
      cloudSearch.indexing().datasources().deleteSchema(resourceName).execute();
      System.out.println("Schema deleted.");
    } catch (GoogleJsonResponseException e) {
      System.err.println("Unable to delete schema: " + e.getDetails());
    } catch (IOException e) {
      System.err.println("Unable to delete schema: " + e.getMessage());
    }
    // [END delete_schema]
  }
}
