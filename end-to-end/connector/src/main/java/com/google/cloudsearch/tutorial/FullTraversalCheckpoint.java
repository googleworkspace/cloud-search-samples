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

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Key;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Checkpoint state for full traversals. For this connector,
 * a single GitHub repository is considered a unit of work. The checkpoint
 * contains the list of repositories still to be indexed.
 * <p>
 * The checkpoint reduces the amount of work that needs to be retried
 * in the event of an error. Instead of resuming the traversal from the
 * beginning, traversal is resumed after the last successful repository
 * to be indexed.
 */
public class FullTraversalCheckpoint extends GenericJson {
  private static JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  /**
   * List of repositories remaining to be indexed.
   */
  @Key
  private List<String> remainingRepositories;

  /**
   * Default constructor for JSON deserializaton.
   */
  public FullTraversalCheckpoint() {
    this(Collections.emptyList());
  }

  /**
   * Creates a checkpoint saving the list of repositories yet to be indexed.
   *
   * @param remainingRepositories List of repos
   */
  public FullTraversalCheckpoint(List<String> remainingRepositories) {
    this.remainingRepositories = remainingRepositories;
  }

  /**
   * Restores a checkpoint from serialized form.
   *
   * @param bytes serialized checkpoint
   * @return New checkpoint instance
   * @throws IOException if unable to decode
   */
  public static FullTraversalCheckpoint fromBytes(byte[] bytes) throws IOException {
    try(InputStream input = new ByteArrayInputStream(bytes)) {
      return JSON_FACTORY.fromInputStream(input, FullTraversalCheckpoint.class);
    }
  }

  /**
   * Get the list of repositories to index.
   *
   * @return list of repos in the form {org}/{repository}
   */
  public List<String> getRemainingRepositories() {
    return remainingRepositories;
  }

  /**
   * Encodes the checkpoint to a byte[] as required by the SDK.
   *
   * @return Encoded checkpoint
   * @throws IOException if unable to encode the checkpoint.
   */
  public byte[] toBytes() throws IOException {
    return JSON_FACTORY.toByteArray(this);
  }
}
