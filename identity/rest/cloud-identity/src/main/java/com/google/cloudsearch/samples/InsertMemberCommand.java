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
import com.google.api.services.cloudidentity.v1beta1.model.*;

import java.util.Collections;
import java.util.List;

@Parameters(commandDescription = "Insert group member")
class InsertMemberCommand implements Runnable {
  @Parameter(names = "--id-source", required = true,
      description = "Identity source ID")
  private String idSource;

  @Parameter(names = "--group-id", required = true,
      description = "Group ID")
  private String groupId;

  @Parameter(names = "--member-id", required = true,
      description = "User email or group ID")
  private String memberId;

  @Parameter(names = "--member-namespace"
      , description = "Namespace for the member ID. Omit for email based IDs")
  private String memberNamespace;

  public void run() {
    try {
      CloudIdentity service = Utils.buildCloudIdentityService();
      String namespace = "identitysources/" + idSource;

      LookupGroupNameResponse lookupResponse = service.groups().lookup()
          .setGroupKeyId(groupId)
          .setGroupKeyNamespace(namespace)
          .execute();

      EntityKey memberKey = new EntityKey().setId(memberId);
      if (memberNamespace != null) {
        memberKey.setNamespace(memberNamespace);
      }

      List<MembershipRole> roles = Collections.singletonList
          (new MembershipRole().setName("MEMBER"));
      Membership member =
          new Membership()
              .setMemberKey(memberKey)
              .setRoles(roles);
      Operation insertMemberOperation = service.groups().memberships()
          .create(lookupResponse.getName(), member)
          .execute();
      if (insertMemberOperation.getDone()) {
        // Note: The response contains the data for a Membership object,
        // but as individual fields. To convert to a Membership instance, either
        // populate the fields individually or serialize & deserialize
        // to/from JSON.
        //
        // Example:
        // String json = service.getJsonFactory().toString(response);
        // Membership membership =  service.getObjectParser()
        //   .parseAndClose(new StringReader(json), Membership.class);
        System.out.printf("Inserted member: %s",
            insertMemberOperation.toPrettyString());
      } else {
        // TODO: Handle case where operation not yet complete, poll
        // for completion. API is currently synchronous and all operations
        // return as completed.
        System.out.printf("Response: %s\n", insertMemberOperation.toPrettyString());
      }
    } catch (Exception e) {
      System.err.printf("Unable to insert member: %s\n", e);
      e.printStackTrace(System.err);
    }
  }
}
