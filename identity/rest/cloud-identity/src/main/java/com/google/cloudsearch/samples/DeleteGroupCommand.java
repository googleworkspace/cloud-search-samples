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
import com.google.api.services.cloudidentity.v1beta1.model.LookupGroupNameResponse;
import com.google.api.services.cloudidentity.v1beta1.model.Operation;

@Parameters(commandDescription = "Delete a group")
class DeleteGroupCommand implements Runnable {
  @Parameter(names = "--id-source", required = true,
      description = "Identity source ID")
  private String idSource;

  @Parameter(names = "--group-id", required = true, description = "Group ID")
  private String groupId;

  public void run() {

    try {
      CloudIdentity service = Utils.buildCloudIdentityService();
      String namespace = "identitysources/" + idSource;
      LookupGroupNameResponse lookupResponse = service.groups().lookup()
          .setGroupKeyId(groupId)
          .setGroupKeyNamespace(namespace)
          .execute();
      String groupResourceName = lookupResponse.getName();
      Operation deleteOperation = service.groups()
          .delete(groupResourceName)
          .execute();
      if (deleteOperation.getDone()) {
        System.out.printf("Deleted group: %s\n", groupResourceName);
      } else {
        // TODO: Handle case where operation not yet complete, poll
        // for completion. API is currently synchronous and all operations
        // return as completed.
        System.out.printf("Response: %s\n", deleteOperation.toPrettyString());
      }
    } catch (Exception e) {
      System.err.printf("Unable to delete group: %s\n", e.getMessage());
      e.printStackTrace(System.err);
    }
  }
}
