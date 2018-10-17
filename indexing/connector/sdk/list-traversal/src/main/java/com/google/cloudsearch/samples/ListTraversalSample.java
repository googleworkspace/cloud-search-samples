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
import com.google.api.services.cloudsearch.v1.model.PushItem;
import com.google.common.primitives.Longs;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterable;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterableImpl;
import com.google.enterprise.cloudsearch.sdk.config.Configuration;
import com.google.enterprise.cloudsearch.sdk.indexing.*;
import com.google.enterprise.cloudsearch.sdk.indexing.template.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
// [END cloud_search_content_sdk_imports]

/**
 * A sample connector using the Cloud Search SDK.
 *
 * <p>This is a simplified sample connector that takes advantage of the
 * Cloud Search SDK. It illustrates using the listing connector template and
 * queues to detect changes and more efficiently indexing content vs. the
 * full traversal strategy. While the full set of documents are traversed
 * and queued, document content is only transmitted for new or modified
* documents.
 *
 * <p>You must provide a configuration file for the connector. This
 * configuration file (for example: sample-config.properties) is supplied to
 * the connector via a command line argument:
 *
 * <pre>java com.google.cloudsearch.samples.FullTraversalSample \
 * -Dconfig=sample-config.properties
 * </pre>
 *
 * <p>Sample properties file:
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
 * # These are used to schedule the traversals at fixed intervals
 * # For this sample, full traversals every 2 minutes
 * schedule.traversalIntervalSecs=120
 * schedule.performTraversalOnStart=true
 *
 * # Number of synthetic documents to create
 * sample.documentCount=10
 * </pre>
 */
public class ListTraversalSample {
  // [START cloud_search_content_sdk_main]
  /**
   * This sample connector uses the Cloud Search SDK template class for a
   * list traversal connector.
   *
   * @param args program command line arguments
   * @throws InterruptedException thrown if an abort is issued during initialization
   */
  public static void main(String[] args) throws InterruptedException {
    Repository repository = new SampleRepository();
    IndexingConnector connector = new ListingConnector(repository);
    IndexingApplication application = new IndexingApplication.Builder(connector, args).build();
    application.start();
  }
  // [END cloud_search_content_sdk_main]

  /**
   * Sample repository that indexes a set of synthetic documents.
   * <p>
   * By using the SDK provided connector templates, the only code required
   * from the connector developer are the methods from the {@link Repository}
   * class. These are used to perform the actual access of the data for
   * uploading via the API.
   */
  public static class SampleRepository implements Repository {
    /**
     * Log output
     */
    Logger log = Logger.getLogger(SampleRepository.class.getName());

    /**
     * Number of synthetic documents to index.
     */
    private int numberOfDocuments;

    /**
     * Tracks the state of synthetic documents between traversals. Maps the
     * document ID to a timestamp which is mutated between traversals and
     * used to dervive content hashes and document versions.
     */
    private Map<Integer, Long> documents = new HashMap<>();

    /**
     * High water mark for document IDs, used when generating additional
     * docs during mutations.
     */
    private int lastDocumentId = 0;

    SampleRepository() {
    }

    /**
     * Performs any data repository initializations here.
     *
     * @param context the {@link RepositoryContext}, not used here
     */
    @Override
    public void init(RepositoryContext context) {
      log.info("Initializing repository");
      numberOfDocuments = Configuration.getInteger("sample.documentCount", 10).get();
    }

    /**
     * Performs any data repository shut down code here.
     */
    @Override
    public void close() {
      log.info("Closing repository");
    }


    /**
     * Gets all of the existing document IDs from the data repository.
     *
     * <p>This method is called by {@link ListingConnector#traverse()} during
     * <em>full traversals</em>. Every document ID and metadata hash value in
     * the <em>repository</em> is pushed to the Cloud Search queue. Each pushed
     * document is later polled and processed in the {@link #getDoc(Item)} method.
     * <p>
     * The metadata hash values are pushed to aid document change detection. The
     * queue sets the document status depending on the hash comparison. If the
     * pushed ID doesn't yet exist in Cloud Search, the document's status is
     * set to <em>new</em>. If the ID exists but has a mismatched hash value,
     * its status is set to <em>modified</em>. If the ID exists and matches
     * the hash value, its status is unchanged.
     *
     * <p>In every case, the pushed content hash value is only used for
     * comparison. The hash value is only set in the queue during an
     * update (see {@link #getDoc(Item)}).
     *
     * @param checkpoint value defined and maintained by this connector
     * @return this is typically a {@link PushItems} instance
     */
    @Override
    public CheckpointCloseableIterable<ApiOperation> getIds(byte[] checkpoint) {
      log.info("Pushing documents to index");
      // prepare the data repository for the next simulated traversal
      mutate();

      // [START cloud_search_content_sdk_push_ids]
      PushItems.Builder allIds = new PushItems.Builder();
      for (Map.Entry<Integer, Long> entry : this.documents.entrySet()) {
        String documentId = Integer.toString(entry.getKey());
        String hash = this.calculateMetadataHash(entry.getKey());
        PushItem item = new PushItem().setMetadataHash(hash);
        log.info("Pushing " + documentId);
        allIds.addPushItem(documentId, item);
      }
      // [END cloud_search_content_sdk_push_ids]
      // [START cloud_search_content_sdk_checkpoint_iterator]
      ApiOperation pushOperation = allIds.build();
      CheckpointCloseableIterable<ApiOperation> iterator =
        new CheckpointCloseableIterableImpl.Builder<>(
            Collections.singletonList(pushOperation))
        .build();
      return iterator;
      // [END cloud_search_content_sdk_checkpoint_iterator]
    }

    /**
     * Gets a single data repository document.
     *
     * <p>This method is called by the {@link ListingConnector} during a poll
     * of the Cloud Search queue. Each queued document is processed
     * individually depending on its state in the data  repository:
     *
     * <ul>
     * <li>Missing: The document is no longer in the data repository, so it
     * is deleted from Cloud Search.</li>
     * <li>Unmodified: The document is already indexed and it has not changed,
     * so re-push with an unmodified status.</li>
     * <li>New or modified: The document is brand new, or has been modified
     * since it was indexed, so re-index it.</li>
     * </ul>
     *
     * <p>The metadata hash is sent during all <em>new</em> or <em>modified</em>
     * status document  updates. This hash value is stored with the document
     * in the Cloud Search API queue for future comparisons of pushed
     * document IDs (see {@link #getIds(byte[])}).
     *
     * @param item the data repository document to retrieve
     * @return the document's state determines which type of
     *         {@link ApiOperation} is returned:
     * {@link RepositoryDoc}, {@link DeleteItem}, or {@link PushItem}
     */
    @Override
    public ApiOperation getDoc(Item item) {
      log.info(() -> String.format("Checking document %s", item.getName()));

      // [START cloud_search_content_sdk_deleted_item]
      String resourceName = item.getName();
      int documentId = Integer.parseInt(resourceName);
      String status = item.getStatus().getCode();

      if (!documents.containsKey(documentId)) {
        // Document no longer exists -- delete it
        log.info(() -> String.format("Deleting document %s", item.getName()));
        return ApiOperations.deleteItem(resourceName);
      }
      // [END cloud_search_content_sdk_deleted_item]
      // [START cloud_search_content_sdk_unchanged_item]
      String currentHash = this.calculateMetadataHash(documentId);
      if (this.canSkipIndexing(item, currentHash)) {
        // Document neither modified nor deleted, ack the push
        log.info(() -> String.format("Document %s not modified", item.getName()));
        PushItem pushItem = new PushItem().setType("NOT_MODIFIED");
        return new PushItems.Builder().addPushItem(resourceName, pushItem).build();
      }
      // [END cloud_search_content_sdk_unchanged_item]
      // New or modified document, index it.
      log.info(() -> String.format("Updating document %s", item.getName()));
      return buildDocument(documentId);
    }

    /**
     * Creates a document for indexing.
     * <p>
     * For this connector sample, the created document is domain public
     * searchable. The content is a simple text string.
     *
     * @param documentId unique local id for the document
     * @return the fully formed document ready for indexing
     */
    private ApiOperation buildDocument(int documentId) {
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

      // Set metadata hash so queue can detect changes
      String metadataHash = this.calculateMetadataHash(documentId);

      // Using the SDK item builder class to create the document with
      // appropriate attributes. This can be expanded to include metadata
      // fields etc.
      Item item = new IndexingItemBuilder(Integer.toString(documentId))
          .setItemType(IndexingItemBuilder.ItemType.CONTENT_ITEM)
          .setAcl(acl)
          .setUrl(IndexingItemBuilder.FieldOrValue.withValue(viewUrl))
          .setVersion(version)
          .setHash(metadataHash)
          .build();
      // [END cloud_search_content_sdk_build_item]

      // [START cloud_search_content_sdk_build_repository_doc]
      // For this sample, content is just plain text
      String content = String.format("Hello world from sample doc %d", documentId);
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
    // connector, but could be implemented if the data repository supports
    // a way to detect changes.

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
    // might be used in the template and/or custom listing traversal connector
    // implementations.

    /**
     * {@inheritDoc}
     *
     * <p>This method is not required by the FullTraversalConnector and is
     * unimplemented.
     */
    @Override
    public CheckpointCloseableIterable<ApiOperation> getAllDocs(byte[] checkpoint) {
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


    /**
     * Returns a hash of the item's metadata. For the generated documents, a
     * timestamp is used to generate a hash. In production implementation,
     * a cryptographic hash of the actual content should be used.
     *
     * @param documentId document to get hash value of
     * @return Hash value of the document
     */
    private String calculateMetadataHash(int documentId) {
      long timestamp = this.documents.get(documentId);
      return Long.toHexString(timestamp);
    }

    // [START cloud_search_content_sdk_skip_indexing]
    /**
     * Checks to see if an item is already up to date
     *
     * @param previousItem Polled item
     * @param currentHash  Metadata hash of the current github object
     * @return PushItem operation
     */
    private boolean canSkipIndexing(Item previousItem, String currentHash) {
      if (previousItem.getStatus() == null || previousItem.getMetadata() == null) {
        return false;
      }
      String status = previousItem.getStatus().getCode();
      String previousHash = previousItem.getMetadata().getHash();
      return "ACCEPTED".equals(status)
          && previousHash != null
          && previousHash.equals(currentHash);
    }
    // [END cloud_search_content_sdk_skip_indexing]

    /**
     * Simulate changes to the repository by randomly mutate the documents. A
     * subset of documents will be either deleted or modified during traversals
     * and new documents created.
     */
    private void mutate() {
      log.info("Mutating repository.");

      Random r = new Random();
      Map<Integer, Long> newDocuments = new HashMap<>();

      for (int key : this.documents.keySet()) {
        switch (r.nextInt(3)) {
          case 0:
            // Leave document unchanged.
            newDocuments.put(key, this.documents.get(key));
            break;
          case 1:
            // Mark it as modified
            newDocuments.put(key, System.currentTimeMillis());
            break;
          default:
            // Delete the document (omit from map)
        }
      }

      // Create new documents
      int newDocumentCount = this.numberOfDocuments - newDocuments.size();
      for (int i = 0; i < newDocumentCount; ++i) {
        int id = ++lastDocumentId;
        newDocuments.put(id, System.currentTimeMillis());
      }

      // Swap out document set
      this.documents = newDocuments;
    }
  }
}
