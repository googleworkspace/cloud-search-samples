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
import com.google.api.services.cloudsearch.v1.model.GSuitePrincipal;
import com.google.api.services.cloudsearch.v1.model.IndexItemRequest;
import com.google.api.services.cloudsearch.v1.model.Item;
import com.google.api.services.cloudsearch.v1.model.ItemAcl;
import com.google.api.services.cloudsearch.v1.model.ItemMetadata;
import com.google.api.services.cloudsearch.v1.model.ItemStructuredData;
import com.google.api.services.cloudsearch.v1.model.NamedProperty;
import com.google.api.services.cloudsearch.v1.model.Principal;
import com.google.api.services.cloudsearch.v1.model.StructuredDataObject;
import com.google.api.services.cloudsearch.v1.model.TextValues;
import com.google.common.primitives.Longs;
// [END cloud_search_api_imports]
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Sample demonstrating how to upload dictionary entries to create synonyms
 * Reads terms from a CSV file, where the first column is the term to define
 * and each subsequent column is a synonym for that term.
 *
 * This sample also illustrates how to perform basic indexing tasks with
 * the Cloud Search REST API instead of the SDK. Dictionary entries
 * are indexed the same as any other content or structured data.
 */
public class DictionarySample {
  /**
   * Command line arguments used by the sample
   */
  static class Arguments {
    @Parameter(names = "--datasource", required = true, description = "ID of the datasource")
    String dataSourceId;

    @Parameter(description = "Path to dictionary file")
    String dictionaryFile = "dictionary.csv";
  }

  /**
   * Main entry point for the sample.
   *
   * Example:
   * <pre>
   *   # Delete a schema
   *   java com.google.cloudsearch.samples.DictionarySample --datasource my-datasource-id dictionary.csv
   * </pre>
   * @param argv Command line args
   */
  public static void main(String[] argv) {
    Arguments arguments = new Arguments();
    JCommander.newBuilder()
        .addObject(arguments)
        .build()
        .parse(argv);

    DictionarySample sample = new DictionarySample();
    sample.uploadDictionary(arguments.dataSourceId, arguments.dictionaryFile);
  }

  // [START cloud_search_build_api_client]
  /**
   * Builds and initializes the client with service account credentials.
   *
   * @return CloudSearch instance
   * @throws IOException if unable to read credentials
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

  /**
   * Represents a dictionary entry to define. Currently only terms
   * and synonyms are defined as part of an entry.
   */
  static class DictionaryEntry {
    private String term;
    private List<String> synonyms;

    DictionaryEntry(String term, List<String> synonyms) {
      this.term = term;
      this.synonyms = synonyms;
    }

    List<String> getSynonyms() {
      return synonyms;
    }

    String getTerm() {
      return term;
    }
  }

  /**
   * Parses the dictionary file (CSV) into a list of DictionaryEntry items
   *
   * @param dictionaryFilePath path to CSV file containing the dictionary
   */
  List<DictionaryEntry> loadEntries(String dictionaryFilePath) throws IOException {
    try (BufferedReader br = new BufferedReader(new FileReader(dictionaryFilePath))) {
      CSVParser parser = new CSVParser(br, CSVFormat.DEFAULT);
      return StreamSupport.stream(parser.spliterator(), false)
          .filter(record -> !record.get(0).startsWith("#")) // Treat any row starting with # as comment
          .map(record -> { // Convert records
            String term = record.get(0);
            // Collect remaining columns as list of synonyms for the term
            List<String> synonyms = StreamSupport.stream(record.spliterator(), false)
                .skip(1) // Skip term
                .collect(Collectors.toList());
            return new DictionaryEntry(term, synonyms);
          })
          .collect(Collectors.toList());
    }
  }

  // [START cloud_search_upload_dictionary]
  /**
   * Updates the schema for a datasource.
   *
   * @param dataSourceId       Unique ID of the datasource.
   * @param dictionaryFilePath path to CSV file containing the dictionary
   */
  void uploadDictionary(String dataSourceId, String dictionaryFilePath) {
    // Get an authorized client
    CloudSearch cloudSearch;
    try {
      cloudSearch = buildAuthorizedClient();
    } catch (IOException e) {
      System.err.println("Unable to initialize client: " + e.getMessage());
      return;
    }

    // Load the dictionary file
    List<DictionaryEntry> entries;
    try {
      entries = loadEntries(dictionaryFilePath);
    } catch (IOException e) {
      System.err.println("Unable to load dictionary: " + e.getMessage());
      return;
    }

    for (DictionaryEntry entry: entries) {
      // Extract term and synonyms from record
      String resourceName = String.format("datasources/%s/items/%s",
          dataSourceId, entry.getTerm());
      // Grant access to all users in the domain
      Principal domainUsers = new Principal()
          .setGsuitePrincipal(new GSuitePrincipal()
              .setGsuiteDomain(true));
      ItemAcl acl = new ItemAcl().setReaders(
          Collections.singletonList(domainUsers));
      // Use the well-known _dictionaryEntry schema to define the terms
      ItemMetadata itemMetadata = new ItemMetadata()
          .setObjectType("_dictionaryEntry");
      NamedProperty termProperty = new NamedProperty()
          .setName("_term")
          .setTextValues(new TextValues()
              .setValues(Collections.singletonList(entry.getTerm())));
      NamedProperty synonymProperty = new NamedProperty()
          .setName("_synonym")
          .setTextValues(new TextValues()
              .setValues(entry.getSynonyms()));
      ItemStructuredData structuredData = new ItemStructuredData()
          .setObject(new StructuredDataObject()
              .setProperties(Arrays.asList(termProperty, synonymProperty)));
      // Use current time as the version
      String version = Base64.getUrlEncoder().encodeToString(
          Longs.toByteArray(System.currentTimeMillis()));

      // Build the item to index
      Item item = new Item()
          .setItemType("CONTENT_ITEM")
          .setName(resourceName)
          .setAcl(acl)
          .setMetadata(itemMetadata)
          .setStructuredData(structuredData)
          .setVersion(version);
      IndexItemRequest request = new IndexItemRequest()
          .setMode("SYNCHRONOUS")
          .setItem(item);

      // Upload the dictionary item
      try {
        cloudSearch.indexing().datasources().items().index(resourceName, request).execute();
        System.out.printf("Defined term: %s\n", entry.getTerm());
      } catch (GoogleJsonResponseException e) {
        System.err.println("Unable to index item: " + e.getDetails());
      } catch (IOException e) {
        System.err.println("Unable to upload dictionary: " + e.getMessage());
      }
    }
  }
  // [END cloud_search_upload_dictionary]
}
