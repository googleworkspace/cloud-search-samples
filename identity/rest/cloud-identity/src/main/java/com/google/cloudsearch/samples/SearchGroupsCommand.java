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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.api.services.cloudidentity.v1beta1.CloudIdentity;
import com.google.api.services.cloudidentity.v1beta1.model.SearchGroupsResponse;

import java.io.IOException;

@Parameters(commandDescription = "Search groups")
class SearchGroupsCommand implements Runnable {
  @Parameter(names = "--id-source", required = true,
      description = "Identity source ID")
  private String idSource;

  public void run() {
    try {
      CloudIdentity service = Utils.buildCloudIdentityService();
      String namespace = "identitysources/" + idSource;
      String query = String.format("namespace=%s AND labels:system/groups/external",
          namespace);
      CloudIdentity.Groups.Search search = service.groups().search()
          .setQuery(query)
          .setView("FULL")
          .setPageSize(1000);
      do {
        SearchGroupsResponse response = search.execute();
        response.getGroups().forEach(group -> {
          try {
            System.out.printf("Group: %s\n", group.toPrettyString());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
        if (response.getNextPageToken() == null) {
          break;
        }
        search.setPageToken(response.getNextPageToken());
      } while (true);
    } catch (Exception e) {
      System.err.printf("Unable to search groups: %s\n", e.getMessage());
      e.printStackTrace(System.err);
    }
  }
}
