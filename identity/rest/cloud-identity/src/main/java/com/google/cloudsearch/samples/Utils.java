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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.cloudidentity.v1beta1.CloudIdentity;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class Utils {
  static CloudIdentity buildCloudIdentityService()
      throws IOException, GeneralSecurityException {
    GoogleCredential credential = GoogleCredential.getApplicationDefault();
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(Collections.singletonList(
          "https://www.googleapis.com/auth/cloud-identity.groups"
      ));
    }
    return new CloudIdentity.Builder(GoogleNetHttpTransport.newTrustedTransport(),
            JacksonFactory.getDefaultInstance(),
            credential)
        .setApplicationName("Cloud identity samples")
        .build();
  }

  static Directory buildDirectoryService() throws IOException, GeneralSecurityException {
    GoogleCredential credential = GoogleCredential.getApplicationDefault();
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(Collections.singletonList(
          "https://www.googleapis.com/auth/admin.directory.user"
      ));
    }
    return new Directory.Builder(GoogleNetHttpTransport.newTrustedTransport(),
            JacksonFactory.getDefaultInstance(),
            credential)
        .setApplicationName("Cloud identity samples")
        .build();

  }
}
