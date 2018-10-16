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

import com.google.api.services.cloudsearch.v1.model.GSuitePrincipal;
import com.google.api.services.cloudsearch.v1.model.Principal;
import com.google.enterprise.cloudsearch.sdk.indexing.Acl;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Sample illustrating how to map various types of permissions
 * to Cloud Search ACls. Contains two examples based on file systems --
 * one using posix file permissions, the other using ACLs
 */
public class FilePermissionSample {
  /**
   * Displays the equivalent Cloud Search ACL for a filesystem object.
   * Attempts to read both POSIX file permissions as well as file system
   * ACLs. Note that these may or may not be supported by various file
   * systems.
   *
   * This does not perform any indexing of the content, it only displays
   * what the ACL would be for a given item.
   *
   * @param args Command line args. Expects 1 argument -- path to a file
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Please specify a file path");
      System.exit(1);
    }

    Path path = FileSystems.getDefault().getPath(args[0]);
    try {
      Acl acl = mapPosixFilePermissionToCloudSearchAcl(path);
      System.out.println("ACL from posix file permissions: ");
      printAcl(acl);
    } catch (IOException e) {
      System.out.println("Unable to read posix permissions");
    }

    try {
      Acl acl = mapNfsAclToCloudSearchAcl(path);
      System.out.println("ACL from NFS acl: ");
      printAcl(acl);
    } catch (IOException e) {
      System.out.println("Unable to read file ACL");
    }
  }

  // [START cloud_search_content_sdk_posix_acl]
  /**
   * Sample for mapping permissions from a source repository to Cloud Search
   * ACLs. In this example, POSIX file permissions are used a the source
   * permissions.
   *
   * @return Acl
   * @throws IOException if unable to read file permissions
   */
  static Acl mapPosixFilePermissionToCloudSearchAcl(Path pathToFile) throws IOException {
    // Id of the identity source for external user/group IDs. Shown here,
    // but may be omitted in the SDK as it is automatically applied
    // based on the `api.identitySourceId` configuration parameter.
    String identitySourceId = "abcdef12345";

    // Retrieve the file system permissions for the item being indexed.
    PosixFileAttributeView attributeView = Files.getFileAttributeView(
        pathToFile,
        PosixFileAttributeView.class,
        LinkOption.NOFOLLOW_LINKS);

    if (attributeView == null) {
      // Can't read, return empty ACl
      return new Acl.Builder().build();
    }

    PosixFileAttributes attrs = attributeView.readAttributes();
    // [START_EXCLUDE]
    // [START cloud_search_content_sdk_posix_acl_owner]
    // Owner, for search quality.
    // Note that for principals the name is not the primary
    // email address in Cloud Directory, but the local ID defined
    // by the OS. Users and groups must be referred to by their
    // external ID and mapped via an identity source.
    List<Principal> owners = Collections.singletonList(
        Acl.getUserPrincipal(attrs.owner().getName(), identitySourceId)
    );
    // [END cloud_search_content_sdk_posix_acl_owner]

    // [START cloud_search_content_sdk_posix_acl_reader]
    // List of users to grant access to
    List<Principal> readers = new ArrayList<>();

    // Add owner, group, others to readers list if permissions
    // exist. For this example, other is mapped to everyone
    // in the organization.
    Set<PosixFilePermission> permissions = attrs.permissions();
    if (permissions.contains(PosixFilePermission.OWNER_READ)) {
      readers.add(Acl.getUserPrincipal(attrs.owner().getName(), identitySourceId));
    }
    // [START cloud_search_content_sdk_posix_acl_group]
    if (permissions.contains(PosixFilePermission.GROUP_READ)) {
      String externalGroupName = attrs.group().getName();
      Principal group = Acl.getGroupPrincipal(externalGroupName, identitySourceId);
      readers.add(group);
    }
    // [END cloud_search_content_sdk_posix_acl_group]
    if (permissions.contains(PosixFilePermission.OTHERS_READ)) {
      Principal everyone = Acl.getCustomerPrincipal();
      readers.add(everyone);
    }
    // [END cloud_search_content_sdk_posix_acl_reader]

    // [START cloud_search_content_sdk_posix_acl_build]
    // Build the Cloud Search ACL. Note that inheritance of permissions
    // from parents is omitted. See `setInheritFrom()` and `setInheritanceType()`
    // methods on the builder if required by your implementation.
    Acl acl = new Acl.Builder()
        .setReaders(readers)
        .setOwners(owners)
        .build();
    // [END cloud_search_content_sdk_posix_acl_build]
    return acl;
    // [END_EXCLUDE]
  }
  // [END cloud_search_content_sdk_posix_acl]
]

  /**
   * Sample for mapping permissions from a source repository to Cloud Search
   * ACLs. In this example, NFS file system ACls are used as the source
   * permissions.
   *
   * @return Acl
   * @throws IOException if unable to read file permissions
   */
  // [START cloud_search_nfs_acl]
  static Acl mapNfsAclToCloudSearchAcl(Path pathToFile) throws IOException {
    // Id of the identity source for external user/group IDs. Shown here,
    // but may be omitted in the SDK as it is automatically applied
    // based on the `api.identitySourceId` configuration parameter.
    String identitySourceId = "abcdef12345";

    // Retrieve the file system ACLs
    AclFileAttributeView aclView = Files.getFileAttributeView(
        pathToFile,
        AclFileAttributeView.class,
        LinkOption.NOFOLLOW_LINKS);

    if (aclView == null) {
      // Can't read, return empty ACl
      return new Acl.Builder().build();
    }

    // Owner, for search quality and owner: filters
    // Note that for principals the name is not the primary
    // email address in Cloud Directory, but the local ID defined
    // by the OS. Users and groups must be referred to by their
    // external ID and mapped via an identity source. The SDK
    // will automatically prepend the identity source prefix based on
    // the `api.identitySourceId` configuration parameter.
    List<Principal> owners = Collections.singletonList(
        Acl.getUserPrincipal(aclView.getOwner().getName(), identitySourceId)
    );
    // Principals allowed to access item
    List<Principal> allowedReaders = new ArrayList<>();
    // Principals denied access to item
    List<Principal> deniedReaders = new ArrayList<>();

    for(AclEntry entry: aclView.getAcl()) {
      UserPrincipal fsPrincipal = entry.principal();
      Principal cloudSearchPrincipal = fsPrincipal instanceof GroupPrincipal ?
          Acl.getGroupPrincipal(fsPrincipal.getName(), identitySourceId) :
          Acl.getUserPrincipal(fsPrincipal.getName(), identitySourceId);
      if (entry.type() == AclEntryType.ALLOW) {
        allowedReaders.add(cloudSearchPrincipal);
      } else if (entry.type() == AclEntryType.DENY) {
        deniedReaders.add(cloudSearchPrincipal);
      }
    }

    // Build the Cloud Search ACL. Note that inheritance of permissions
    // from parents is omitted. See `setInheritFrom()` and `setInheritanceType()`
    // methods on the builder if required by your implementation.
    return new Acl.Builder()
        .setReaders(allowedReaders)
        .setDeniedReaders(deniedReaders)
        .setOwners(owners)
        .build();
  }
  // [END cloud_search_nfs_acl]

  /**
   * Pretty prints an ACL
   *
   * @param acl
   */
  static void printAcl(Acl acl) {
    System.out.println("Owners:");
    for(Principal p: acl.getOwners()) {
      printPrincipal(p);
    }
    System.out.println("Allowed readers:");
    for(Principal p: acl.getReaders()) {
      printPrincipal(p);
    }
    System.out.println("Denied readers:");
    for(Principal p: acl.getDeniedReaders()) {
      printPrincipal(p);
    }
  }

  /**
   * Pretty prints a principal
   *
   * @param principal
   */
  static void printPrincipal(Principal principal) {
    if (principal.getUserResourceName() != null) {
      System.out.printf("- External user: %s\n", principal.getUserResourceName());
    } else if (principal.getGroupResourceName() != null) {
      System.out.printf("- External group: %s\n", principal.getGroupResourceName());
    } else if (principal.getGsuitePrincipal() != null) {
      GSuitePrincipal gSuitePrincipal = principal.getGsuitePrincipal();
      if (gSuitePrincipal.getGsuiteDomain()) {
        System.out.println("- G Suite domain");
      } else if(gSuitePrincipal.getGsuiteUserEmail() != null) {
        System.out.printf("- G Suite user: %s\n", gSuitePrincipal.getGsuiteUserEmail());
      } else if(gSuitePrincipal.getGsuiteGroupEmail() != null) {
        System.out.printf("- G Suite group: %s\n", gSuitePrincipal.getGsuiteGroupEmail());
      }
    }
  }
}
