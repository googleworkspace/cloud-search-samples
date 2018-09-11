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
import com.google.api.services.cloudidentity.v1beta1.model.EntityKey;
import com.google.api.services.cloudidentity.v1beta1.model.Group;
import com.google.api.services.cloudidentity.v1beta1.model.Operation;

import java.util.Collections;

@Parameters(commandDescription = "Create a group")
class CreateGroupCommand implements Runnable {
  @Parameter(names = "--id-source", required = true,
      description = "Identity source ID")
  private String idSource;

  @Parameter(names = "--group-id", required = true, description = "Group ID")
  private String groupId;

  @Parameter(names = "--display-name", description = "Group display name")
  private String groupName;

  public void run() {
    String namespace = "identitysources/" + idSource;
    Group group = new Group()
        .setGroupKey(new EntityKey().setNamespace(namespace).setId(groupId))
        .setDescription("Demo group")
        .setDisplayName(groupName)
        .setLabels(Collections.singletonMap("system/groups/external", ""))
        .setParent(namespace);
    try {
      CloudIdentity service = Utils.buildCloudIdentityService();
      Operation createOperation = service.groups().create(group).execute();

      if (createOperation.getDone()) {
        // Note: The response contains the data for a Group object, but as
        // individual fields. To convert to a Group instance, either populate
        // the fields individually or serialize & deserialize to/from JSON.
        //
        // Example:
        // String json = service.getJsonFactory().toString(response);
        // Group createdGroup =  service.getObjectParser()
        //     .parseAndClose(new StringReader(json), Group.class);
        System.out.printf("Group: %s\n",
            createOperation.getResponse().toString());
      } else {
        // TODO: Handle case where operation not yet complete, poll for
        // completion. API is currently synchronous and all operations return
        // as completed.
        System.out.printf("Response: %s\n", createOperation.toPrettyString());
      }
    } catch (Exception e) {
      System.err.printf("Unable to create group: %s", e.getMessage());
      e.printStackTrace(System.err);
    }
  }
}
