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

// [START cloud_search_sdk_imports]
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.cloudsearch.v1.model.Item;
import com.google.api.services.cloudsearch.v1.model.PushItem;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.primitives.Longs;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterable;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterableImpl;
import com.google.enterprise.cloudsearch.sdk.indexing.*;
import com.google.enterprise.cloudsearch.sdk.indexing.template.*;

import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
// [END cloud_search_sdk_imports]

/**
 * A sample connector using the Cloud Search SDK.
 *
 * <p>This is a simplified sample connector that takes advantage of the
 * Cloud Search SDK. It illustrates how to use the {@code ListingConnector}
 * template to perform a breadth first traversal of a tree structured
 * repository. This technique can be used for indexing file systems or
 * other repositories with similar structures. he approach taken works for any
 * directed acyclic graph.
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
 * # These are used to schedule the traversals at fixed intervals
 * # For this sample, full traversals every 2 minutes
 * schedule.traversalIntervalSecs=120
 * schedule.performTraversalOnStart=true
 * </pre>
 */
public class GraphTraversalSample {
  /**
   * This sample connector uses the Cloud Search SDK template class for a graph
   * traversal connector.
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
     * Graph which represents the structure of the repository.
     */
    private Graph<String> documents;

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

      // Build a small graph to represent the repository structure to index.
      MutableGraph<String> documents = GraphBuilder
          .directed()
          .allowsSelfLoops(false)
          .build();
      documents.putEdge("root", "root.1");
      documents.putEdge("root.1", "root.1.1");
      documents.putEdge("root.1.1", "root.1.1.1");
      documents.putEdge("root.1.1", "root.1.1.2");
      documents.putEdge("root.1", "root.1.2");
      documents.putEdge("root.1.2", "root.1.2.1");
      documents.putEdge("root.1.2", "root.1.2.2");
      documents.putEdge("root", "root.2");
      documents.putEdge("root.2", "root.2.1");
      documents.putEdge("root.2.1", "root.2.1.1");
      documents.putEdge("root.2.1", "root.2.1.2");
      documents.putEdge("root.2", "root.2.2");
      documents.putEdge("root.2.2", "root.2.2.1");
      documents.putEdge("root.2.2", "root.2.2.2");

      this.documents = ImmutableGraph.copyOf(documents);
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
     * <em>full traversals</em>. Only the root nodes of the graph are pushed
     * during traversals, child nodes are recursively added later in
     * {@link #getDoc} as each node is visited.
     *
     * @param checkpoint value defined and maintained by this connector
     * @return this is typically a {@link PushItems} instance
     */
    @Override
    public CheckpointCloseableIterable<ApiOperation> getIds(byte[] checkpoint) {
      log.info("Pushing root documents to index");

      PushItems.Builder allIds = new PushItems.Builder();
      // Note that on subsequent traversals pushing the root node may not result
      // in the full graph traversal. Unmodified items are withheld from the
      // queue for up to 4 hours. This may delay detection of changes
      // to child nodes until the parent node becomes available.
      // If the repository propagates child modification times upward, use
      // metadata hash values to efficiently re-trigger the traversal.
      // Alternatively, for repositories that enumerate incremental changes,
      // implement {@link #getChanges} to provide incremental updates.
      PushItem item = new PushItem();
      allIds.addPushItem("root", item);

      ApiOperation pushOperation = allIds.build();
      return new CheckpointCloseableIterableImpl.Builder<>(
          Collections.singletonList(pushOperation))
          .build();
    }

    /**
     * Gets a single data repository document.
     * <p>
     * This method is called by the {@link ListingConnector} during a poll of
     * the Cloud Search queue. If the document has child nodes, this returns
     * both the current node to index as well as a list of document ids to
     * push to the queue. The end result is a breadth first traversal of the graph.
     *
     * @param item the data repository document to retrieve
     * @return the document's state determines which type of
     * {@link ApiOperation} is returned:
     * {@link RepositoryDoc}, {@link DeleteItem}, or {@link PushItem}
     */
    @Override
    public ApiOperation getDoc(Item item) {
      log.info(() -> String.format("Indexing document %s", item.getName()));

      String resourceName = item.getName();
      if (documentExists(resourceName)) {
        return buildDocumentAndChildren(resourceName);
      } else {
        // Document doesn't exist, delete it
        log.info(() -> String.format("Deleting document %s", resourceName));
        return ApiOperations.deleteItem(resourceName);
      }
    }

    /**
     * Creates a document for indexing.
     * <p>
     * For this connector sample, the created document is domain public
     * searchable. The content is a simple text string. In addition to indexing
     * the current document, the child nodes are also pushed into the queue.
     * This method will later be called for the child nodes as they're pulled
     * from the queue.
     *
     * @param documentId unique local id for the document
     * @return the fully formed document ready for indexing
     */
    private ApiOperation buildDocumentAndChildren(String documentId) {
      // Make the document publicly readable within the domain
      Acl acl = new Acl.Builder()
          .setReaders(Collections.singletonList(Acl.getCustomerPrincipal()))
          .build();

      // Url is required. Use google.com as a placeholder for this sample.
      String viewUrl = "https://www.google.com";

      // Version is required, set to current timestamp.
      byte[] version = Longs.toByteArray(System.currentTimeMillis());

      // Using the SDK item builder class to create the document with
      // appropriate attributes. This can be expanded to include metadata
      // fields etc.
      Item item = new IndexingItemBuilder(documentId)
          .setItemType(IndexingItemBuilder.ItemType.CONTENT_ITEM)
          .setAcl(acl)
          .setUrl(IndexingItemBuilder.FieldOrValue.withValue(viewUrl))
          .setVersion(version)
          .build();

      // For this sample, content is just plain text
      String content = String.format("Hello world from sample doc %s", documentId);
      ByteArrayContent byteContent = ByteArrayContent.fromString("text/plain", content);

      RepositoryDoc.Builder docBuilder = new RepositoryDoc.Builder()
          .setItem(item)
          .setContent(byteContent, IndexingService.ContentFormat.TEXT);

      // Queue the child nodes to visit after indexing this document
      Set<String> childIds = getChildItemNames(documentId);
      for (String id : childIds) {
        log.info(() -> String.format("Pushing child node %s", id));
        PushItem pushItem = new PushItem();
        docBuilder.addChildId(id, pushItem);
      }

      return docBuilder.build();
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
     * Checks to see if the document exists in the graph.
     *
     * @param documentId Id of document to check
     * @return true if exists, false otherwise
     */
    private boolean documentExists(String documentId) {
      return this.documents.nodes().contains(documentId);
    }

    /**
     * Returns the set of child nodes in the graph.
     *
     * @param documentId Id of document to retrieve children of
     * @return Set of child document names
     */
    private Set<String> getChildItemNames(String documentId) {
      return this.documents.successors(documentId);
    }
  }
}