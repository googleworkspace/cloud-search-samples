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
// [START cloud_search_api_imports]
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.cloudsearch.v1.CloudSearch;
import com.google.api.services.cloudsearch.v1.model.Operation;
import com.google.api.services.cloudsearch.v1.model.Schema;
import com.google.api.services.cloudsearch.v1.model.Status;
import com.google.api.services.cloudsearch.v1.model.UpdateSchemaRequest;
// [END cloud_search_api_imports]

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;

/**
 * Sample demonstrating how to update the schema for a datasource. Reads
 * the schema from a JSON file.
 */
public class SchemaSample {

  public static final int OPERATION_POLL_INTERVAL = 3 * 1000;

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

  // [START cloud_search_build_api_client]
  /**
   * Builds and initializes the client with service account credentials.
   * @return CloudSearch instance
   * @throws IOException if unable to load credentials
   */
  private CloudSearch buildAuthorizedClient() throws IOException {
    // Get the service account credentials based on the GOOGLE_APPLICATION_CREDENTIALS
    // environment variable
    GoogleCredential credential = GoogleCredential.getApplicationDefault(
        Utils.getDefaultTransport(),
        Utils.getDefaultJsonFactory());
    // Ensure credentials have the correct scope
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(Collections.singletonList(
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
  }
  // [START cloud_search_build_api_client]

  // [START cloud_search_update_schema]
  /**
   * Updates the schema for a datasource.
   *
   * @param dataSourceId   Unique ID of the datasource.
   * @param schemaFilePath path to JSON file containing the schema
   */
   void updateSchema(String dataSourceId, String schemaFilePath) {
    try {
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
        operation = cloudSearch.operations().get(operation.getName()).execute();
      }

      // Operation is complete, check result
      Status error = operation.getError();
      if (error != null) {
        System.err.println("Error updating schema:" + error.getMessage());
      } else {
        System.out.println("Schema updated.");
      }
    } catch (GoogleJsonResponseException e) {
      System.err.println("Unable to update schema: " + e.getDetails());
    } catch (IOException e) {
      System.err.println("Unable to update schema: " + e.getMessage());
    } catch (InterruptedException e) {
      System.err.println("Interrupted while waiting for schema update: "
          + e.getMessage());
    }
   }
  // [END cloud_search_update_schema]



  /**
   * Retrieves the schema for a datasource.
   *
   * @param dataSourceId Unique ID of the datasource.
   */
  void getSchema(String dataSourceId) {
    // [START cloud_search_get_schema]
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
    // [END cloud_search_get_schema]
  }

  // [START cloud_search_delete_schema]
  /**
   * Deletes the schema for a datasource.
   *
   * @param dataSourceId Unique ID of the datasource.
   */
  void deleteSchema(String dataSourceId) {
    try {
      CloudSearch cloudSearch = buildAuthorizedClient();
      String resourceName = String.format("datasources/%s", dataSourceId);
      Operation operation = cloudSearch.indexing().datasources()
          .deleteSchema(resourceName)
          .execute();

      // Wait for the operation to complete.
      while (operation.getDone() == null || operation.getDone() == false) {
        // Wait before polling again
        Thread.sleep(OPERATION_POLL_INTERVAL);
        operation = cloudSearch.operations().get(operation.getName()).execute();
      }

      // Operation is complete, check result
      Status error = operation.getError();
      if (error != null) {
        System.err.println("Error deleting schema:" + error.getMessage());
      } else {
        System.out.println("Schema deleted.");
      }
      System.out.println("Schema deleted.");
    } catch (GoogleJsonResponseException e) {
      System.err.println("Unable to delete schema: " + e.getDetails());
    } catch (IOException e) {
      System.err.println("Unable to delete schema: " + e.getMessage());
    } catch (InterruptedException e) {
      System.err.println("Interrupted while waiting for schema delete: "
          + e.getMessage());
    }
  }
  // [END cloud_search_delete_schema]
}
