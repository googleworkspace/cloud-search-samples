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

import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.util.DateTime;
import com.google.api.services.cloudsearch.v1.model.Item;
import com.google.api.services.cloudsearch.v1.model.PushItem;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Longs;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterable;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterableImpl;
import com.google.enterprise.cloudsearch.sdk.InvalidConfigurationException;
import com.google.enterprise.cloudsearch.sdk.RepositoryException;
import com.google.enterprise.cloudsearch.sdk.StartupException;
import com.google.enterprise.cloudsearch.sdk.config.ConfigValue;
import com.google.enterprise.cloudsearch.sdk.config.Configuration;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingItemBuilder;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingItemBuilder.FieldOrValue;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingService;
import com.google.enterprise.cloudsearch.sdk.indexing.template.*;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpException;

import javax.activation.FileTypeMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repository implementation for indexing content from one or more GitHub repos.
 * This is a sample implementation and not intended for production use.
 *
 * @see GithubConnector for configuration details.
 */
public class GithubRepository implements Repository {
  /**
   * Log output
   */
  private Logger log = Logger.getLogger(GithubRepository.class.getName());

  /**
   * Organizations or users to index
   */
  private List<String> githubOrganizations;

  /**
   * Github client
   */
  private GitHub github;

  /**
   * Regex to parse GitHub URL paths.
   */
  private Pattern githubPathPattern = Pattern.compile(
      "/([^/]+/[^/]+)/?(issues|pull|blob/[^/]+|tree/[^/]+)?/?(.*)");


  GithubRepository() {
  }

  /**
   * Initializes the connection to GitHub as well as the list
   * of repositories to index.
   *
   * @param context the {@link RepositoryContext}, not used here
   */
  @Override
  public void init(RepositoryContext context) throws StartupException {
    log.info("Initializing repository");

    ConfigValue<List<String>> repos = Configuration.getMultiValue(
        "github.repos",
        Collections.emptyList(),
        Configuration.STRING_PARSER);

    ConfigValue<String> user = Configuration.getString(
        "github.user", null);

    ConfigValue<String> token = Configuration.getString(
        "github.token", null);

    this.githubOrganizations = repos.get();

    if (this.githubOrganizations.isEmpty()) {
      throw new InvalidConfigurationException(
          "No repositories configured. Set 'github.repos' in the configuration" +
              " to one or more repositories."
      );
    }

    if (user.get() == null || user.get().trim().isEmpty()) {
      throw new InvalidConfigurationException(
          "No github user configured. Set 'github.user'" +
              " in the configuration to a valid github account.");
    }

    if (token.get() == null || token.get().trim().isEmpty()) {
      throw new InvalidConfigurationException(
          "No github access token configured. Set" +
              " 'github.token' in the configuration to a valid github account.");
    }

    if (github == null ) {
      try {
        github = new GitHubBuilder()
            .withPassword(user.get().trim(), token.get().trim())
            .build();
      } catch (IOException e) {
        throw new InvalidConfigurationException("Unable to connect to GitHub", e);
      }
    }

    try {
      // Validate connection
      github.getMyself();
    } catch (IOException e) {
      throw new InvalidConfigurationException("Unable to connect to GitHub", e);
    }
  }

  /**
   * Gets all of the existing item IDs from the data repository. While
   * multiple repositories are supported, only one repository is traversed
   * per call. The remaining repositories are saved in the checkpoint
   * are traversed on subsequent calls. This minimizes the amount of
   * data that needs to be reindex in the event of an error.
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
  // [START cloud_search_github_tutorial_get_ids]
  @Override
  public CheckpointCloseableIterable<ApiOperation> getIds(byte[] checkpoint)
      throws RepositoryException {
    // [START cloud_search_github_tutorial_decode_checkpoint]
    List<String> repositories;
    // Decode the checkpoint if present to get the list of remaining
    // repositories to index.
    if (checkpoint != null) {
      try {
        FullTraversalCheckpoint decodedCheckpoint = FullTraversalCheckpoint
            .fromBytes(checkpoint);
        repositories = decodedCheckpoint.getRemainingRepositories();
      } catch (IOException e) {
        throw new RepositoryException.Builder()
            .setErrorMessage("Unable to deserialize checkpoint")
            .setCause(e)
            .build();
      }
    } else {
      // No previous checkpoint, scan for repositories to index
      // based on the connector configuration.
      try {
        repositories = scanRepositories();
      } catch (IOException e) {
        throw toRepositoryError(e, Optional.of("Unable to scan repositories"));
      }
    }
    // [END cloud_search_github_tutorial_decode_checkpoint]

    if (repositories.isEmpty()) {
      // Nothing left to index. Reset the checkpoint to null so the
      // next full traversal starts from the beginning
      Collection<ApiOperation> empty = Collections.emptyList();
      return new CheckpointCloseableIterableImpl.Builder<>(empty)
          .setCheckpoint((byte[]) null)
          .setHasMore(false)
          .build();
    }

    // Still have more repositories to index. Pop the next repository to
    // index off the list. The remaining repositories make up the next
    // checkpoint.
    String repositoryToIndex = repositories.get(0);
    repositories = repositories.subList(1, repositories.size());

    try {
      log.info(() -> String.format("Traversing repository %s", repositoryToIndex));
      Collection<ApiOperation> items = collectRepositoryItems(repositoryToIndex);
      FullTraversalCheckpoint newCheckpoint = new FullTraversalCheckpoint(repositories);
      return new CheckpointCloseableIterableImpl.Builder<>(items)
          .setHasMore(true)
          .setCheckpoint(newCheckpoint.toBytes())
          .build();
    } catch (IOException e) {
      String errorMessage = String.format("Unable to traverse repo: %s",
          repositoryToIndex);
      throw toRepositoryError(e, Optional.of(errorMessage));
    }
  }
  // [END cloud_search_github_tutorial_get_ids]


  /**
   * Gets a single data repository item and indexes it if required.
   *
   * <p>This method is called by the {@link ListingConnector} during a poll
   * of the Cloud Search queue. Each queued item is processed
   * individually depending on its state in the data repository.
   *
   * @param item the data repository item to retrieve
   * @return the item's state determines which type of
   * {@link ApiOperation} is returned:
   * {@link RepositoryDoc}, {@link DeleteItem}, or {@link PushItem}
   */
  // [START cloud_search_github_tutorial_get_doc]
  @Override
  public ApiOperation getDoc(Item item) throws RepositoryException {
    log.info(() -> String.format("Processing item: %s ", item.getName()));
    Object githubObject;
    try {
      // Retrieve the item from GitHub
      githubObject = getGithubObject(item.getName());
      if (githubObject instanceof GHRepository) {
        return indexItem((GHRepository) githubObject, item);
      } else if (githubObject instanceof GHPullRequest) {
        return indexItem((GHPullRequest) githubObject, item);
      } else if (githubObject instanceof GHIssue) {
        return indexItem((GHIssue) githubObject, item);
      } else if (githubObject instanceof GHContent) {
        return indexItem((GHContent) githubObject, item);
      } else {
        String errorMessage = String.format("Unexpected item received: %s",
            item.getName());
        throw new RepositoryException.Builder()
            .setErrorMessage(errorMessage)
            .setErrorType(RepositoryException.ErrorType.UNKNOWN)
            .build();
      }
    } catch (FileNotFoundException e) {
      log.info(() -> String.format("Deleting item: %s ", item.getName()));
      return ApiOperations.deleteItem(item.getName());
    } catch (IOException e) {
      String errorMessage = String.format("Unable to retrieve item: %s",
          item.getName());
      throw toRepositoryError(e, Optional.of(errorMessage));
    }
  }
  // [END cloud_search_github_tutorial_get_doc]

  /**
   * Retrieves an item from the GitHub API based on the path. Expects paths
   * in any of the following forms:
   * <ul>
   * <li>{owner}/{repo}/issues/{id}</li>
   * <li>{owner}/{repo}/pull/{id}</li>
   * <li><{owner}/{repo}/blob/{branch}/{path}/li>
   * </ul>
   *
   * @param path Path portion of a github URL
   * @return Item from GitHub (either GHObject subclass or GHContent)
   * @throws FileNotFoundException if item no longer exists
   * @throws IOException           if unable to read item
   */
  private Object getGithubObject(String path) throws IOException {
    Matcher matcher = githubPathPattern.matcher(path);

    if (!matcher.matches()) {
      String errorMessage = String.format("Unable to match path: %s", path);
      throw new IOException(errorMessage);
    }

    String repoName = matcher.group(1);
    String type = matcher.group(2);
    String id = matcher.group(3);

    GHRepository repo = github.getRepository(repoName);
    if (type == null) {
      return repo;
    } else if (type.equals("issues")) {
      return repo.getIssue(Integer.parseInt(id));
    } else if (type.equals("pull")) {
      return repo.getPullRequest(Integer.parseInt(id));
    } else if (type.startsWith("blob/")) {
      // This is a bit of a hack to work around GitHub API limitations.
      // Fetch a content item directly is limited to items < 1mb in size.
      // However, fetching a directory returns the metadata without the content.
      // When indexing a file, fetch the parent instead and locate the item
      // in the list.
      String[] dirComponents = id.split("/");
      String[] parentComponents = Arrays.copyOfRange(dirComponents,
          0,
          dirComponents.length - 1);
      String parentPath = String.join("/", parentComponents);
      List<GHContent> files = repo.getDirectoryContent(parentPath);
      for (GHContent f : files) {
        if (f.getPath().equals(id)) {
          return f;
        }
      }
      String errorMessage = String.format("Unable to retrieve content item: %s ", path);
      throw new FileNotFoundException(errorMessage);
    }

    String errorMessage = String.format("Invalid type %s for path %s", type, path);
    throw new IOException(errorMessage);
  }

  /**
   * Build the ApiOperation to index a repository.
   *
   * @param repo         Repository
   * @param previousItem Previous item state in the index
   * @return ApiOperation (RepositoryDoc if indexing,  PushItem if not modified)
   * @throws IOException if unable to create operation
   */
  private ApiOperation indexItem(GHRepository repo, Item previousItem)
      throws IOException {
    String metadataHash = repo.getUpdatedAt().toString();

    // If previously indexed and unchanged, just requeue as unmodified
    if (canSkipIndexing(previousItem, metadataHash)) {
      return notModified(previousItem.getName());
    }

    String resourceName = repo.getHtmlUrl().getPath();
    FieldOrValue<String> title = FieldOrValue.withValue(repo.getFullName());
    FieldOrValue<String> url = FieldOrValue.withValue(repo.getHtmlUrl().toExternalForm());
    FieldOrValue<DateTime> creationTime = FieldOrValue.withValue(
        new DateTime(repo.getCreatedAt().getTime()));

    // Structured data based on the schema
    Multimap<String, Object> structuredData = ArrayListMultimap.create();
    structuredData.put("organization", repo.getOwnerName());
    structuredData.put("repository", repo.getName());
    structuredData.put("stars", repo.getStargazersCount());
    structuredData.put("forks", repo.getForks());
    structuredData.put("openIssues", repo.getOpenIssueCount());
    structuredData.put("watchers", repo.getWatchers());
    structuredData.put("createdAt", repo.getCreatedAt());
    structuredData.put("updatedAt", repo.getUpdatedAt());

    // Create the item to index
    Item item = new IndexingItemBuilder(resourceName)
        .setTitle(title)
        .setUrl(url)
        .setItemType(IndexingItemBuilder.ItemType.CONTAINER_ITEM)
        .setObjectType("repository")
        .setValues(structuredData)
        .setVersion(Longs.toByteArray(repo.getUpdatedAt().getTime()))
        .setCreationTime(creationTime)
        .setHash(metadataHash)
        .build();

    // TODO - Render markdown to HTML?
    AbstractInputStreamContent content = new ByteArrayContent(
        "text/plain",
        repo.getDescription().getBytes(StandardCharsets.UTF_8));
    return new RepositoryDoc.Builder()
        .setItem(item)
        .setRequestMode(IndexingService.RequestMode.SYNCHRONOUS)
        .setContent(content, IndexingService.ContentFormat.TEXT)
        .setRequestMode(IndexingService.RequestMode.SYNCHRONOUS)
        .build();
  }

  /**
   * Build the ApiOperation to index a pull request.
   *
   * @param pullRequest  Pull request to index
   * @param previousItem Previous item state in the index
   * @return ApiOperation (RepositoryDoc if indexing,  PushItem if not modified)
   * @throws IOException if unable to create operation
   */
  private ApiOperation indexItem(GHPullRequest pullRequest, Item previousItem)
      throws IOException {
    String metadataHash = pullRequest.getUpdatedAt().toString();

    // If previously indexed and unchanged, just requeue as unmodified
    if (canSkipIndexing(previousItem, metadataHash)) {
      return notModified(previousItem.getName());
    }

    String resourceName = pullRequest.getHtmlUrl().getPath();
    FieldOrValue<String> title = FieldOrValue.withValue(pullRequest.getTitle());
    FieldOrValue<String> url = FieldOrValue.withValue(
        pullRequest.getHtmlUrl().toExternalForm());
    FieldOrValue<DateTime> creationTime = FieldOrValue.withValue(
        new DateTime(pullRequest.getCreatedAt().getTime()));
    String containerName = pullRequest.getRepository().getHtmlUrl().getPath();

    // Structured data based on the schema
    Multimap<String, Object> structuredData = ArrayListMultimap.create();
    structuredData.put("organization", pullRequest.getRepository().getOwnerName());
    structuredData.put("repository", pullRequest.getRepository().getName());
    structuredData.put("status", pullRequest.getState().name().toLowerCase());
    structuredData.put("openedBy", pullRequest.getUser() != null ?
        pullRequest.getUser().getLogin() : null);
    structuredData.put("assignee", pullRequest.getAssignee() != null ?
        pullRequest.getAssignee().getLogin() : null);
    for (GHLabel label : pullRequest.getLabels()) {
      structuredData.put("labels", label.getName());
    }

    // Index comments as sub objects in the metadata. This makes the comments
    // searchable but still tied to the issue itself.
    for (GHIssueComment comment : pullRequest.getComments()) {
      Multimap<String, Object> commentData = ArrayListMultimap.create();
      commentData.put("comment", comment.getBody());
      commentData.put("user", comment.getUser() != null ?
          comment.getUser().getLogin() : null);
      structuredData.put("comments", commentData);
    }
    structuredData.put("createdAt", pullRequest.getCreatedAt());
    structuredData.put("updatedAt", pullRequest.getUpdatedAt());

    Item item = new IndexingItemBuilder(resourceName)
        .setTitle(title)
        .setContainer(containerName)
        .setUrl(url)
        .setItemType(IndexingItemBuilder.ItemType.CONTAINER_ITEM)
        .setObjectType("pullRequest")
        .setValues(structuredData)
        .setVersion(Longs.toByteArray(pullRequest.getUpdatedAt().getTime()))
        .setCreationTime(creationTime)
        .setHash(metadataHash)
        .build();

    // TODO - Index the actual patch/diff?
    // TODO - Render markdown to HTML
    AbstractInputStreamContent content = new ByteArrayContent(
        "text/plain",
        pullRequest.getBody().getBytes(StandardCharsets.UTF_8));
    return new RepositoryDoc.Builder()
        .setItem(item)
        .setContent(content, IndexingService.ContentFormat.TEXT)
        .setRequestMode(IndexingService.RequestMode.SYNCHRONOUS)
        .build();
  }

  /**
   * Build the ApiOperation to index an issue.
   *
   * @param issue        Pull request to index
   * @param previousItem Previous item state in the index
   * @return ApiOperation (RepositoryDoc if indexing,  PushItem if not modified)
   * @throws IOException if unable to create operation
   */
  private ApiOperation indexItem(GHIssue issue, Item previousItem)
      throws IOException {
    String metadataHash = issue.getUpdatedAt().toString();

    // If previously indexed and unchanged, just requeue as unmodified
    if (canSkipIndexing(previousItem, metadataHash)) {
      return notModified(previousItem.getName());
    }

    String resourceName = issue.getHtmlUrl().getPath();
    FieldOrValue<String> title = FieldOrValue.withValue(issue.getTitle());
    FieldOrValue<String> url = FieldOrValue.withValue(
        issue.getHtmlUrl().toExternalForm());
    FieldOrValue<DateTime> creationTime = FieldOrValue.withValue(
        new DateTime(issue.getCreatedAt().getTime()));
    String containerName = issue.getRepository().getHtmlUrl().getPath();

    // Structured data based on the schema
    Multimap<String, Object> structuredData = ArrayListMultimap.create();
    structuredData.put("organization", issue.getRepository().getOwnerName());
    structuredData.put("repository", issue.getRepository().getName());
    structuredData.put("status", issue.getState().name().toLowerCase());
    structuredData.put("reportedBy", issue.getUser() != null ?
        issue.getUser().getLogin() : null);
    structuredData.put("assignee", issue.getAssignee() != null ?
        issue.getAssignee().getLogin() : null);
    for (GHLabel label : issue.getLabels()) {
      structuredData.put("labels", label.getName());
    }

    // Index comments as sub objects in the metadata. This makes the comments
    // searchable but still tied to the issue itself.
    for (GHIssueComment comment : issue.getComments()) {
      Multimap<String, Object> commentData = ArrayListMultimap.create();
      commentData.put("comment", comment.getBody());
      commentData.put("user", comment.getUser() != null ?
          comment.getUser().getLogin() : null);
      structuredData.put("comments", commentData);
    }
    structuredData.put("createdAt", issue.getCreatedAt());
    structuredData.put("updatedAt", issue.getUpdatedAt());

    Item item = new IndexingItemBuilder(resourceName)
        .setTitle(title)
        .setContainer(containerName)
        .setUrl(url)
        .setItemType(IndexingItemBuilder.ItemType.CONTAINER_ITEM)
        .setObjectType("issue")
        .setValues(structuredData)
        .setVersion(Longs.toByteArray(issue.getUpdatedAt().getTime()))
        .setCreationTime(creationTime)
        .setHash(metadataHash)
        .build();

    // TODO - Render markdown to HTML
    AbstractInputStreamContent content = new ByteArrayContent(
        "text/plain",
        issue.getBody().getBytes(StandardCharsets.UTF_8));
    return new RepositoryDoc.Builder()
        .setItem(item)
        .setContent(content, IndexingService.ContentFormat.TEXT)
        .setRequestMode(IndexingService.RequestMode.SYNCHRONOUS)
        .build();
  }

  /**
   * Build the ApiOperation to index a content item (file).
   *
   * @param content      Content item to index
   * @param previousItem Previous item state in the index
   * @return ApiOperation (RepositoryDoc if indexing,  PushItem if not modified)
   * @throws IOException if unable to create operation
   */
  private ApiOperation indexItem(GHContent content, Item previousItem)
      throws IOException {
    String metadataHash = content.getSha();

    // If previously indexed and unchanged, just requeue as unmodified
    if (canSkipIndexing(previousItem, metadataHash)) {
      return notModified(previousItem.getName());
    }

    String resourceName = new URL(content.getHtmlUrl()).getPath();
    FieldOrValue<String> title = FieldOrValue.withValue(content.getName());
    FieldOrValue<String> url = FieldOrValue.withValue(content.getHtmlUrl());

    String containerName = content.getOwner().getHtmlUrl().getPath();
    String programmingLanguage = FileExtensions.getLanguageForFile(content.getName());

    // Structured data based on the schema
    Multimap<String, Object> structuredData = ArrayListMultimap.create();
    structuredData.put("organization", content.getOwner().getOwnerName());
    structuredData.put("repository", content.getOwner().getName());
    structuredData.put("path", content.getPath());
    structuredData.put("language", programmingLanguage);

    Item item = new IndexingItemBuilder(resourceName)
        .setTitle(title)
        .setContainer(containerName)
        .setUrl(url)
        .setItemType(IndexingItemBuilder.ItemType.CONTAINER_ITEM)
        .setObjectType("file")
        .setValues(structuredData)
        .setVersion(Longs.toByteArray(System.currentTimeMillis()))
        .setHash(content.getSha())
        .build();

    // Index the file content too
    String mimeType = FileTypeMap.getDefaultFileTypeMap()
        .getContentType(content.getName());
    System.out.println("MT= " + mimeType);
    AbstractInputStreamContent fileContent = new InputStreamContent(
        mimeType, content.read())
        .setLength(content.getSize())
        .setCloseInputStream(true);
    return new RepositoryDoc.Builder()
        .setItem(item)
        .setContent(fileContent, IndexingService.ContentFormat.RAW)
        .setRequestMode(IndexingService.RequestMode.SYNCHRONOUS)
        .build();
  }

  /**
   * Collects the names of repositories to index. Expands
   *
   * @return List of repository names (org/name)
   * @throws IOException if unable to query GitHub
   */
  private List<String> scanRepositories() throws IOException {
    List<String> repositoryNames = new ArrayList<>();
    for (String name : githubOrganizations) {
      if (name.contains("/")) {
        // Name is a fully qualified repo, not an org. Just add it.
        repositoryNames.add(name);
      } else {
        // Name is just an org, scan for repos
        GHOrganization organization = github.getOrganization(name);
        Map<String, GHRepository> repos = organization.getRepositories();
        for (GHRepository r : repos.values()) {
          repositoryNames.add(r.getFullName());
        }
      }
    }
    return repositoryNames;
  }


  /**
   * Fetch IDs to  push in to the queue for all items in the repository.
   * Currently captures issues & content in the master branch.
   *
   * @param name Name of repository to index
   * @return Items to push into the queue for later indexing
   * @throws IOException if error reading issues
   */
  // [START cloud_search_github_tutorial_collect_repository_items]
  private Collection<ApiOperation> collectRepositoryItems(String name)
      throws IOException {
    List<ApiOperation> operations = new ArrayList<>();
    GHRepository repo = github.getRepository(name);

    // Add the repository as an item to be indexed
    String metadataHash = repo.getUpdatedAt().toString();
    String resourceName = repo.getHtmlUrl().getPath();
    PushItem repositoryPushItem = new PushItem()
        .setMetadataHash(metadataHash);
    PushItems items = new PushItems.Builder()
        .addPushItem(resourceName, repositoryPushItem)
        .build();

    operations.add(items);
    // Add issues/pull requests & files
    operations.add(collectIssues(repo));
    operations.add(collectContent(repo));
    return operations;
  }
  // [END cloud_search_github_tutorial_collect_repository_items]

  /**
   * Fetch all issues for the repository. Includes pull requests.
   *
   * @param repo Repository to get issues for
   * @return Items to push into the queue for later indexing
   * @throws IOException if error reading issues
   */
  private PushItems collectIssues(GHRepository repo) throws IOException {
    PushItems.Builder builder = new PushItems.Builder();

    List<GHIssue> issues = repo.listIssues(GHIssueState.ALL)
        .withPageSize(1000)
        .asList();
    for (GHIssue issue : issues) {
      String resourceName = issue.getHtmlUrl().getPath();
      log.info(() -> String.format("Adding issue %s", resourceName));
      PushItem item = new PushItem();
      item.setMetadataHash(Long.toHexString(issue.getUpdatedAt().getTime()));
      builder.addPushItem(resourceName, item);
    }
    return builder.build();
  }

  /**
   * Walks the directory tree collecting files in the master branch.
   *
   * @param repo Repository to walk
   * @return Items to push into the queue for later indexing
   * @throws IOException if error reading files
   */
  private PushItems collectContent(GHRepository repo) throws IOException {
    PushItems.Builder builder = new PushItems.Builder();
    collectContentRecursively(builder, repo, "/");
    return builder.build();
  }

  /**
   * Walks the directory tree collecting files in the master branch.
   *
   * @param builder PushItems builder to add IDs too
   * @param repo    Repository to walk
   * @param path    Current path
   * @throws IOException if error reading files
   */
  private void collectContentRecursively(PushItems.Builder builder,
                                         GHRepository repo,
                                         String path) throws IOException {
    List<GHContent> contents = repo.getDirectoryContent(path);
    for (GHContent contentItem : contents) {
      if (contentItem.isDirectory()) {
        collectContentRecursively(builder, repo, contentItem.getPath());
      } else {
        String resourceName = new URL(contentItem.getHtmlUrl()).getPath();
        log.info(() -> String.format("Adding file %s", resourceName));
        PushItem item = new PushItem();
        item.setMetadataHash(contentItem.getSha());
        builder.addPushItem(resourceName, item);
      }
    }
  }

  /**
   * Checks to see if an item needs reindex
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

  /**
   * Builds an API operation to mark an item as not modified since
   * previously indexed.
   *
   * @param resourceName name of item
   * @return PushItem operation
   */
  private ApiOperation notModified(String resourceName) {
    PushItem notModified = new PushItem()
        .setType("NOT_MODIFIED");
    return new PushItems.Builder()
        .addPushItem(resourceName, notModified)
        .build();
  }

  //
  // The following methods are not used in this sample.
  //

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
   * <p>This method is not required by the ListingConnector and is unimplemented.
   */
  @Override
  public boolean exists(Item item) {
    return false;
  }

  /**
   * {@inheritDoc}
   * <p>Not implemented by this repository.</p>
   */
  @Override
  public void close() {
  }

  /**
   * For testing -- allow injecting GitHub client
   * @param client GitHub client to use
   */
  public void setGitHub(GitHub client) {
    this.github = client;
  }

  private RepositoryException toRepositoryError(IOException e, Optional<String> message) {
    if (e instanceof HttpException) {
      int code = ((HttpException) e).getResponseCode();
      String responseMessage = ((HttpException) e).getResponseMessage();
      RepositoryException.ErrorType type = RepositoryException.ErrorType.SERVER_ERROR;

      if (code == 401) {
        type = RepositoryException.ErrorType.AUTHENTICATION_ERROR;
      } else if (code == 403 || code == 429) {
        type = RepositoryException.ErrorType.QUOTA_EXCEEDED;
      } else {
        type = RepositoryException.ErrorType.SERVER_ERROR;
      }
      return new RepositoryException.Builder()
          .setErrorMessage(message.orElse(responseMessage))
          .setCause(e)
          .setErrorCode(code)
          .setErrorType(type)
          .build();
    } else {
      return new RepositoryException.Builder()
          .setErrorMessage(message.orElse(e.getMessage()))
          .setCause(e)
          .setErrorType(RepositoryException.ErrorType.SERVER_ERROR)
          .build();
    }
  }
}
