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
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.model.User;

@Parameters(commandDescription = "Retrieve a user")
class GetUserCommand implements Runnable {
  @Parameter(names = "--email", required = true,
      description = "User's primary email")
  private String userEmail;

  public void run() {
    try {
      Directory service = Utils.buildDirectoryService();
      User user = service.users().get(userEmail)
          .setProjection("full")
          .execute();
      System.out.printf("User: %s\n", user.toPrettyString());
    } catch (Exception e) {
      System.err.printf("Unable to retrieve user: %s\n", e);
      e.printStackTrace(System.err);
    }
  }
}
