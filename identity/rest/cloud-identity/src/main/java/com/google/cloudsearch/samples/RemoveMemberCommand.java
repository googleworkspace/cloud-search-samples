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
import com.google.api.services.cloudidentity.v1beta1.model.LookupGroupNameResponse;
import com.google.api.services.cloudidentity.v1beta1.model.LookupMembershipNameResponse;
import com.google.api.services.cloudidentity.v1beta1.model.Operation;

@Parameters(commandDescription = "Remove a group member")
class RemoveMemberCommand implements Runnable {
  @Parameter(names = "--id-source", required = true,
      description = "Identity source ID")
  private String idSource;

  @Parameter(names = "--group-id", required = true, description = "Group ID")
  private String groupId;

  @Parameter(names = "--member-id", required = true,
      description = "User email or group ID")
  private String memberId;

  @Parameter(names = "--member-namespace",
      description = "Namespace for the member ID. Omit for email based IDs")
  private String memberNamespace;

  public void run() {
    try {
      CloudIdentity service = Utils.buildCloudIdentityService();
      String namespace = "identitysources/" + idSource;

      LookupGroupNameResponse lookupGroupNameResponse = service.groups().lookup()
          .setGroupKeyId(groupId)
          .setGroupKeyNamespace(namespace)
          .execute();

      EntityKey memberKey = new EntityKey().setId(memberId);
      if (memberNamespace != null) {
        memberKey.setNamespace(memberNamespace);
      }

      LookupMembershipNameResponse lookupMembershipNameResponse =
          service.groups().memberships()
              .lookup(lookupGroupNameResponse.getName())
              .setMemberKeyId(memberKey.getId())
              .setMemberKeyNamespace(memberKey.getNamespace())
              .execute();

      String resourceName = lookupMembershipNameResponse.getName();
      Operation removeMemberOperation = service.groups().memberships()
          .delete(resourceName)
          .execute();
      if (removeMemberOperation.getDone()) {
        System.out.print("Removed member\n");
      } else {
        // TODO: Handle case where operation not yet complete, poll
        // for completion. API is currently synchronous and all operations
        // return as completed.
        System.out.printf("Response: %s\n", removeMemberOperation.toPrettyString());
      }
    } catch (Exception e) {
      System.err.printf("Unable to remove member: %s\n ", e.getMessage());
      e.printStackTrace(System.err);
    }
  }
}
