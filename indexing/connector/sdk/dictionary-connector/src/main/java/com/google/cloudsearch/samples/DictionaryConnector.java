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

// [START cloud_search_content_sdk_imports]
import com.google.api.services.cloudsearch.v1.model.Item;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterable;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterableImpl;
import com.google.enterprise.cloudsearch.sdk.RepositoryException;
import com.google.enterprise.cloudsearch.sdk.StartupException;
import com.google.enterprise.cloudsearch.sdk.config.Configuration;
import com.google.enterprise.cloudsearch.sdk.indexing.Acl;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingApplication;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingConnector;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingItemBuilder;
import com.google.enterprise.cloudsearch.sdk.indexing.template.ApiOperation;
import com.google.enterprise.cloudsearch.sdk.indexing.template.FullTraversalConnector;
import com.google.enterprise.cloudsearch.sdk.indexing.template.Repository;
import com.google.enterprise.cloudsearch.sdk.indexing.template.RepositoryContext;
import com.google.enterprise.cloudsearch.sdk.indexing.template.RepositoryDoc;
// [END cloud_search_content_sdk_imports]
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * A sample connector using the Cloud Search SDK.
 *
 * <p>This is a simplified sample connector that takes advantage of the Cloud
 * Search SDK for defining synonyms. The connector uses the full traversal
 * template which is suitable for small repositories or ones with limited
 * capabilities.
 *
 * <p>You must provide a configuration file for the connector. This
 * configuration file (for example: sample-config.properties) is supplied to the
 * connector via a command line argument:
 *
 * <pre>java com.google.cloudsearch.samples.DictionaryConnector \
 *   -Dconfig=sample-config.properties
 * </pre>
 *
 * <p>Sample configuration file:
 *
 * <pre>
 * # Required properties for accessing data source
 * # (These values are created by the admin before running the connector)
 * api.sourceId=1234567890abcdef
 * api.serviceAccountPrivateKeyFile=./PrivateKey.json
 *
 * # This simple sample only needs to run one time and exit
 * connector.runOnce=true
 *
 * # Path to dictionary file to upload
 * dictionary.file=dictionary.csv
 * </pre>
 */
public class DictionaryConnector {

  /**
   * This sample connector uses the Cloud Search SDK template class for a full
   * traversal connector.
   *
   * @param args program command line arguments
   * @throws InterruptedException thrown if an abort is issued during initialization
   */
  public static void main(String[] args) throws InterruptedException {
    Repository repository = new DictionaryRepository();
    IndexingConnector connector = new FullTraversalConnector(repository);
    IndexingApplication application = new IndexingApplication.Builder(connector, args).build();
    application.start();
  }

  /**
   * Sample repository that indexes a set of synthetic documents.
   *
   * By using the SDK provided connector templates, the only code required from
   * the connector developer are the methods from the {@link Repository} class.
   * These are used to perform the actual access of the data for uploading via
   * the API.
   */
  public static class DictionaryRepository implements Repository {

    /** Log output */
    private static final Logger log = Logger.getLogger(DictionaryRepository.class.getName());

    private static final Acl DOMAIN_PUBLIC_ACL =
        new Acl.Builder().setReaders(ImmutableList.of(Acl.getCustomerPrincipal())).build();

    /** Dictionary file to load */
    String dictionaryFilePath;

    DictionaryRepository() {
    }

    /**
     * Performs any data repository initializations here.
     *
     * @param context the {@link RepositoryContext}, not used here
     */
    @Override
    public void init(RepositoryContext context) {
      log.info("Initializing repository");
      dictionaryFilePath = Configuration.getString("dictionary.file", "dictionary.csv").get();
      if (dictionaryFilePath == null) {
        throw new StartupException("Missing dictionary.file parameter in configuration");
      }
      if (Files.notExists(Paths.get(dictionaryFilePath))) {
        throw new StartupException("Dictionary file does not exist.");
      }
    }

    /**
     * Performs any data repository shut down code here.
     */
    @Override
    public void close() {
      log.info("Closing repository");
    }

    /**
     * Gets all the data repository documents.
     *
     * This is the core of the {@link Repository} implemented code for a full
     * traversal connector. A complete traversal of the entire data repository
     * is performed here.
     *
     * For this sample there are only a small set of statically created documents
     * defined.
     *
     * @param checkpoint save state from last iteration
     * @return An iterator of {@link RepositoryDoc} instances
     */
    @Override
    public CheckpointCloseableIterable<ApiOperation> getAllDocs(byte[] checkpoint)
        throws RepositoryException {
      log.info("Retrieving all documents.");

      CSVFormat csvFormat = CSVFormat.RFC4180.withIgnoreEmptyLines()
          .withIgnoreSurroundingSpaces()
          .withCommentMarker('#');
      try (BufferedReader br = new BufferedReader(new FileReader(dictionaryFilePath));
          CSVParser parser = new CSVParser(br, csvFormat)) {
        List<ApiOperation> allDocs = StreamSupport.stream(parser.spliterator(), false)
            .map(this::buildDocument)
            .collect(Collectors.toList());
        return new CheckpointCloseableIterableImpl.Builder<>(allDocs).build();
      } catch (IOException e) {
        throw new RepositoryException.Builder()
            .setCause(e)
            .setErrorType(RepositoryException.ErrorType.CLIENT_ERROR)
            .build();
      }
    }

    // [START cloud_search_content_sdk_index_term]
    /**
     * Creates a document for indexing.
     *
     * For this connector sample, the created document is domain public
     *  searchable. The content is a simple text string.
     *
     * @param record The current CSV record to convert
     * @return the fully formed document ready for indexing
     */
    private ApiOperation buildDocument(CSVRecord record) {
      // Extract term and synonyms from record
      String term = record.get(0);
      List<String> synonyms = StreamSupport.stream(record.spliterator(), false)
          .skip(1) // Skip term
          .collect(Collectors.toList());

      Multimap<String, Object> structuredData = ArrayListMultimap.create();
      structuredData.put("_term", term);
      structuredData.putAll("_synonym", synonyms);

      String itemName = String.format("dictionary/%s", term);

      // Using the SDK item builder class to create the item
      Item item =
          IndexingItemBuilder.fromConfiguration(itemName)
              .setItemType(IndexingItemBuilder.ItemType.CONTENT_ITEM)
              .setObjectType("_dictionaryEntry")
              .setValues(structuredData)
              .setAcl(DOMAIN_PUBLIC_ACL)
              .build();

      // Create the fully formed document
      return new RepositoryDoc.Builder()
          .setItem(item)
          .build();
    }
    // [END cloud_search_content_sdk_index_term]

    // The following method is not used in this simple full traversal sample
    // connector, but could be implemented if the data repository supports a
    //  way to detect changes.

    /**
     * {@inheritDoc}
     *
     * <p>This method is not required by the FullTraversalConnector and is
     * unimplemented.
     */
    @Override
    public CheckpointCloseableIterable<ApiOperation> getChanges(byte[] checkpoint) {
      return null;
    }

    // The following methods are not used in the full traversal connector, but
    // might be used in the template and/or custom listing traversal
    // connector implementations.

    /**
     * {@inheritDoc}
     *
     * <p>This method is not required by the FullTraversalConnector and is
     * unimplemented.
     */
    @Override
    public CheckpointCloseableIterable<ApiOperation> getIds(byte[] checkpoint) {
      return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method is not required by the FullTraversalConnector and is
     * unimplemented.
     */
    @Override
    public ApiOperation getDoc(Item item) {
      return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method is not required by the FullTraversalConnector and is
     * unimplemented.
     */
    @Override
    public boolean exists(Item item) {
      return false;
    }
  }
}
