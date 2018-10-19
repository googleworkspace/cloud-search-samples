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
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.cloudsearch.v1.model.Item;
import com.google.common.primitives.Longs;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterable;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterableImpl;
import com.google.enterprise.cloudsearch.sdk.config.Configuration;
import com.google.enterprise.cloudsearch.sdk.indexing.Acl;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingApplication;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingConnector;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingItemBuilder;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingService;
import com.google.enterprise.cloudsearch.sdk.indexing.template.ApiOperation;
import com.google.enterprise.cloudsearch.sdk.indexing.template.FullTraversalConnector;
import com.google.enterprise.cloudsearch.sdk.indexing.template.Repository;
import com.google.enterprise.cloudsearch.sdk.indexing.template.RepositoryContext;
import com.google.enterprise.cloudsearch.sdk.indexing.template.RepositoryDoc;

import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.stream.IntStream;
// [END cloud_search_content_sdk_imports]

/**
 * A sample connector using the Cloud Search SDK.
 *
 * <p>This is a simplified  sample connector that takes advantage of the Cloud
 * Search SDK. The connector uses the full traversal template which is
 * suitable for small repositories or ones with limited capabilities. The
 * full set of documents in the repository are indexed with each traversal.
 *
 * <p>You must provide a configuration file for the connector. This
 * configuration file (for example: sample-config.properties) is supplied to the
 * connector via a command line argument:
 *
 * <pre>java com.google.cloudsearch.samples.FullTraversalSample \
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
 * # Number of synthetic documents to create
 * sample.documentCount=10
 * </pre>
 */
public class FullTraversalSample {

  // [START cloud_search_content_sdk_main]
  /**
   * This sample connector uses the Cloud Search SDK template class for a full
   * traversal connector.
   *
   * @param args program command line arguments
   * @throws InterruptedException thrown if an abort is issued during initialization
   */
  public static void main(String[] args) throws InterruptedException {
    Repository repository = new SampleRepository();
    IndexingConnector connector = new FullTraversalConnector(repository);
    IndexingApplication application = new IndexingApplication.Builder(connector, args).build();
    application.start();
  }
  // [END cloud_search_content_sdk_main]

  /**
   * Sample repository that indexes a set of synthetic documents.
   *
   * By using the SDK provided connector templates, the only code required from
   * the connector developer are the methods from the {@link Repository} class.
   * These are used to perform the actual access of the data for uploading via
   * the API.
   */
  public static class SampleRepository implements Repository {

    /** Log output */
    Logger log = Logger.getLogger(SampleRepository.class.getName());

    /** Number of synthetic documents to index. */
    private int numberOfDocuments;

    SampleRepository() {
    }

    /**
     * Performs any data repository initializations here.
     *
     * @param context the {@link RepositoryContext}, not used here
     */
    // [START cloud_search_content_sdk_init]
    @Override
    public void init(RepositoryContext context) {
      log.info("Initializing repository");
      numberOfDocuments = Configuration.getInteger("sample.documentCount", 10).get();
    }
    // [END cloud_search_content_sdk_init]

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
    public CheckpointCloseableIterable<ApiOperation> getAllDocs(byte[] checkpoint) {
      log.info("Retrieving all documents.");

      Iterator<ApiOperation> allDocs = IntStream.range(0, numberOfDocuments)
          .mapToObj(this::buildDocument)
          .iterator();
      // [START cloud_search_content_sdk_checkpoint_iterator]
      CheckpointCloseableIterable<ApiOperation> iterator =
        new CheckpointCloseableIterableImpl.Builder<>(allDocs).build();
      // [END cloud_search_content_sdk_checkpoint_iterator]
      return iterator;
    }

    /**
     * Creates a document for indexing.
     *
     * For this connector sample, the created document is domain public
     *  searchable. The content is a simple text string.
     *
     * @param id unique local id for the document
     * @return the fully formed document ready for indexing
     */
    private ApiOperation buildDocument(int id) {
      // [START cloud_search_content_sdk_domain_acl]
      // Make the document publicly readable within the domain
      Acl acl = new Acl.Builder()
          .setReaders(Collections.singletonList(Acl.getCustomerPrincipal()))
          .build();
      // [END cloud_search_content_sdk_domain_acl]

      // [START cloud_search_content_sdk_build_item]
      // Url is required. Use google.com as a placeholder for this sample.
      String viewUrl = "https://www.google.com";

      // Version is required, set to current timestamp.
      byte[] version = Longs.toByteArray(System.currentTimeMillis());

      // Using the SDK item builder class to create the document with appropriate attributes
      // (this can be expanded to include metadata fields etc.)
      Item item = IndexingItemBuilder.fromConfiguration(Integer.toString(id))
          .setItemType(IndexingItemBuilder.ItemType.CONTENT_ITEM)
          .setAcl(acl)
          .setSourceRepositoryUrl(IndexingItemBuilder.FieldOrValue.withValue(viewUrl))
          .setVersion(version)
          .build();
      // [END cloud_search_content_sdk_build_item]

      // [START cloud_search_content_sdk_build_repository_doc]
      // For this sample, content is just plain text
      String content = String.format("Hello world from sample doc %d", id);
      ByteArrayContent byteContent = ByteArrayContent.fromString("text/plain", content);

      // Create the fully formed document
      RepositoryDoc doc = new RepositoryDoc.Builder()
          .setItem(item)
          .setContent(byteContent, IndexingService.ContentFormat.TEXT)
          .build();
      // [END cloud_search_content_sdk_build_repository_doc]
      return doc;
    }

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
