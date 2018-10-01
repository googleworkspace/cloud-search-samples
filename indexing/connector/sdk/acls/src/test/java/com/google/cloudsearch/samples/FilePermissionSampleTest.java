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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.enterprise.cloudsearch.sdk.indexing.Acl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class FilePermissionSampleTest {
  FileSystem fileSystem;

  @Before
  public void createFileSystem() {
    Configuration filesystemConfig = Configuration.unix().toBuilder()
        .setAttributeViews("basic", "owner", "posix", "unix", "acl")
        .setWorkingDirectory("/home/user")
        .setBlockSize(4096)
        .build();
    fileSystem = Jimfs.newFileSystem(filesystemConfig);
  }

  @Test
  public void testPosixOwnerOnly() throws IOException {
    Path path = createTestFileWithPosixPermissions(
        user("100"),
        group("200"),
        PosixFilePermission.OWNER_READ);

    Acl acl = FilePermissionSample.mapPosixFilePermissionToCloudSearchAcl(path);
    assertThat(acl.getReaders()).hasSize(1);
    List<String> userPrincipalNames = acl.getReaders().stream()
        .map(principal -> principal.getUserResourceName())
        .collect(Collectors.toList());
    assertThat(userPrincipalNames).contains("identitysources/abcdef12345/users/100");
  }

  @Test
  public void testPosixWithGroup() throws IOException {
    Path path = createTestFileWithPosixPermissions(
        user("100"),
        group("200"),
        PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ);

    Acl acl = FilePermissionSample.mapPosixFilePermissionToCloudSearchAcl(path);
    assertThat(acl.getReaders()).hasSize(2);
    List<String> groupPrincipalNames = acl.getReaders().stream()
        .filter(principal -> principal.getGroupResourceName() != null)
        .map(principal -> principal.getGroupResourceName())
        .collect(Collectors.toList());
    assertThat(groupPrincipalNames).contains("identitysources/abcdef12345/groups/200");
  }

  @Test
  public void testPosixWithGroupAndOther() throws IOException {
    Path path = createTestFileWithPosixPermissions(
        user("100"),
        group("200"),
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.GROUP_READ);

    Acl acl = FilePermissionSample.mapPosixFilePermissionToCloudSearchAcl(path);
    assertThat(acl.getReaders()).hasSize(3);
    assertThat(acl.getReaders().stream()
        .filter(principal -> principal.getGsuitePrincipal() != null &&
            principal.getGsuitePrincipal().getGsuiteDomain())
        .count()).isEqualTo(1);
  }

  @Test
  public void testAcl() throws IOException {
    Path path = createTestFileWithAcl(
        user("100"),
        allow(user("100")),
        allow(user("200")),
        deny(group("300")));

    Acl acl = FilePermissionSample.mapNfsAclToCloudSearchAcl(path);
    assertThat(acl.getReaders()).hasSize(2);
    assertThat(acl.getDeniedReaders()).hasSize(1);

    List<String> userPrincipalNames = acl.getReaders().stream()
        .filter(principal -> principal.getUserResourceName() != null)
        .map(principal -> principal.getUserResourceName())
        .collect(Collectors.toList());
    assertThat(userPrincipalNames)
        .containsAllOf("identitysources/abcdef12345/users/100",
            "identitysources/abcdef12345/users/200");

    List<String> groupPrincipalNames = acl.getDeniedReaders().stream()
        .map(principal -> principal.getGroupResourceName())
        .collect(Collectors.toList());
    assertThat(groupPrincipalNames).contains("identitysources/abcdef12345/groups/300");
  }

  // Creates a test file with the given posix permissions
  private Path createTestFileWithPosixPermissions(UserPrincipal owner,
                                                  UserPrincipal group,
                                                  PosixFilePermission... permissions) throws IOException {
    Set<PosixFilePermission> permissionAsSet = new HashSet<>();
    Collections.addAll(permissionAsSet, permissions);

    Path path = fileSystem.getPath("/test.txt");
    Files.createFile(path);
    Files.setAttribute(path, "owner:owner", owner);
    Files.setAttribute(path, "posix:group", group);
    Files.setAttribute(path, "posix:permissions", permissionAsSet);
    return path;
  }

  // Creates a test file with the given ACl
  private Path createTestFileWithAcl(UserPrincipal owner,
                                     AclEntry... entries) throws IOException {
    Path path = fileSystem.getPath("/test.txt");
    Files.createFile(path);
    Files.setAttribute(path, "owner:owner", owner);
    Files.setAttribute(path, "acl:acl", Arrays.asList(entries));
    return path;
  }

  // Convenience methods for building principals & acl entries

  UserPrincipal user(String name) throws IOException {
    return fileSystem.getUserPrincipalLookupService().lookupPrincipalByName(name);
  }

  GroupPrincipal group(String name) throws IOException {
    return fileSystem.getUserPrincipalLookupService().lookupPrincipalByGroupName(name);
  }

  AclEntry allow(UserPrincipal principal) {
    return AclEntry.newBuilder()
        .setPrincipal(principal)
        .setType(AclEntryType.ALLOW)
        .build();
  }

  AclEntry deny(UserPrincipal principal) {
    return AclEntry.newBuilder()
        .setPrincipal(principal)
        .setType(AclEntryType.DENY)
        .build();
  }
}
