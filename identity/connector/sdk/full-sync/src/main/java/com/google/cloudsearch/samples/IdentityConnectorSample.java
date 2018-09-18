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

import com.google.api.services.cloudidentity.v1beta1.model.EntityKey;
import com.google.api.services.cloudidentity.v1beta1.model.Membership;
import com.google.api.services.cloudidentity.v1beta1.model.MembershipRole;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterable;
import com.google.enterprise.cloudsearch.sdk.CheckpointCloseableIterableImpl;
import com.google.enterprise.cloudsearch.sdk.config.Configuration;
import com.google.enterprise.cloudsearch.sdk.identity.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A sample identity connector using the Cloud Search SDK.
 *
 * <p>This is a simplified sample connector that takes advantage of the Cloud
 * Search Identity Connector SDK. The connector uses the full sync template
 * for syncing external user identities and group rosters to Cloud Directory.
 **
 * <p>You must provide a configuration file for the connector. This
 * configuration file (for example: sample-config.properties) is supplied to the
 * connector via a command line argument:
 *
 * <pre>java com.google.cloudsearch.samples.IdentityConnectorSample \
 *   -Dconfig=sample-config.properties
 * </pre>
 *
 * <p>Sample configuration file:
 *
 * <pre>
 * # Required properties for accessing data source
 * # (These values are created by the admin before running the connector)
 * api.identitySourceId=abcdef0123456789
 * api.serviceAccountPrivateKeyFile=./PrivateKey.json
 * api.customerId=abc123
 *
 * # This simple sample only needs to run one time and exit
 * connector.runOnce=true
 *
 * # Paths to CSV files
 * sample.usersFile=users.csv
 * sample.groupsFile=groups.csv
 * </pre>
 *
 * The user CSV file maps Google accounts to an external identifier. The format
 * of the CSV file is two columns. The first is the user's primary email
 * address in Cloud Directory, the second is the value of the external ID
 * to associate with the user.
 *
 * The group CSV file enumerates externally managed groups and their members.
 * The first column represents the group name or ID, and subsequent columns
 * are members.
 *
 * Members that are Google users or email-based groups are identified
 * by the email address. To add a member that is another external group
 * synced to the identity source, prefix the name of the group with "gid:".
 *
 * The following example creates 5 groups
 * where the first 4 have users as members and the 5th is a roll-up of
 * all the members of the previous 4 groups:
 *
 * <pre>
 *   sales-na, user1@example.com, user2@example.com, ...
 *   sales-eu, user3@example.com, ...
 *   sales-latam, user5@example.com, ...
 *   sales-apac, user7@example.com, ...
 *   all-sales, gid:sales-na, gid:sales-eu, gid:sales-latam, gid:sales-apac
 * </pre>
 */
public class IdentityConnectorSample {

  /**
   * This sample connector uses the Cloud Search SDK template class for a full
   * sync connector. In the full sync case, the repository is responsible
   * for providing a snapshot of the complete identity mappings and
   * group rosters. This is then reconciled against the current set
   * of mappings and groups in Cloud Directory.
   *
   * @param args program command line arguments
   * @throws InterruptedException thrown if an abort is issued during initialization
   */
  public static void main(String[] args) throws InterruptedException {
    Repository repository = new CsvRepository();
    IdentityConnector connector = new FullSyncIdentityConnector(repository);
    IdentityApplication application = new IdentityApplication.Builder(connector, args).build();
    application.start();
  }

  /**
   * Sample repository that syncs users and groups from a set of CSV files.
   * <p>
   * By using the SDK provided connector templates, the only code required from
   * the connector developer are the methods from the {@link Repository} class.
   * These are used to perform the actual access of the data for uploading via
   * the API.
   */
  public static class CsvRepository implements Repository {

    /**
     * Injected context, provides convenience methods for
     * building users & groups
     */
    private RepositoryContext context;

    /** Path to user CSV file */
    private String userMappingCsvPath;

    /** Path to group CSV file */
    private String groupMappingCsvPath;

    /**
     * Log output
     */
    Logger log = Logger.getLogger(CsvRepository.class.getName());

    CsvRepository() {}

    /**
     * Initializes the repository once the SDK is initialized.
     *
     * @param context Injected context, contains convenienve methods
     *                for building users & groups
     * @throws IOException if unable to initialize.
     */
    @Override
    public void init(RepositoryContext context) throws IOException {
      log.info("Initializing repository");
      this.context = context;
      userMappingCsvPath = Configuration.getString(
          "sample.usersFile", "users.csv").get();
      groupMappingCsvPath = Configuration.getString(
          "sample.groupsFile", "groups.csv").get();
    }

    /**
     * Retrieves all user identity mappings for the identity source. For the
     * full sync connector, the repository must provide a complete snapshot
     * of the mappings. This is reconciled against the current mappings
     * in Cloud Directory. All identity mappings returned here are
     * set in Cloud Directory. Any previously mapped users that are omitted
     * are unmapped.
     *
     * The connector does not create new users. All users are assumed to
     * exist in Cloud Directory.
     *
     * @param checkpoint Saved state if paging over large result sets. Not used
     *                   for this sample.
     * @return Iterator of user identity mappings
     * @throws IOException if unable to read user identity mappings
     */
    @Override
    public CheckpointCloseableIterable<IdentityUser> listUsers(byte[] checkpoint)
        throws IOException {
      List<IdentityUser> users = new ArrayList<>();
      try (Reader in = new FileReader(userMappingCsvPath)) {
        // Read user mappings from CSV file
        CSVParser parser = CSVFormat.RFC4180
            .withIgnoreSurroundingSpaces()
            .withIgnoreEmptyLines()
            .withCommentMarker('#')
            .parse(in);
        for (CSVRecord record : parser.getRecords()) {
          // Each record is in form: "primary_email", "external_id"
          String primaryEmailAddress = record.get(0);
          String externalId = record.get(1);
          if (primaryEmailAddress.isEmpty() || externalId.isEmpty()) {
            // Skip any malformed mappings
            continue;
          }
          log.info(() -> String.format("Adding user %s/%s",
              primaryEmailAddress, externalId));

          // Add the identity mapping
          IdentityUser user = context.buildIdentityUser(
              primaryEmailAddress, externalId);
          users.add(user);
        }
      }
      return new CheckpointCloseableIterableImpl.Builder<IdentityUser>(users)
          .setHasMore(false)
          .setCheckpoint((byte[])null)
          .build();
    }

    /**
     * Retrieves all group rosters for the identity source. For the
     * full sync connector, the repository must provide a complete snapshot
     * of the rosters. This is reconciled against the current rosters
     * in Cloud Directory. All groups and members  returned here are
     * set in Cloud Directory. Any previously created groups or members
     * that are omitted are removed.
     *
     * @param checkpoint Saved state if paging over large result sets. Not used
     *                   for this sample.
     * @return Iterator of group rosters
     * @throws IOException if unable to read groups
     */    @Override
    public CheckpointCloseableIterable<IdentityGroup> listGroups(byte[] checkpoint)
        throws IOException {
      List<IdentityGroup> groups = new ArrayList<>();
      try (Reader in = new FileReader(groupMappingCsvPath)) {
        // Read group rosters from CSV
        CSVParser parser = CSVFormat.RFC4180
            .withIgnoreSurroundingSpaces()
            .withIgnoreEmptyLines()
            .withCommentMarker('#')
            .parse(in);
        for (CSVRecord record : parser.getRecords()) {
          // Each record is in form: "group_id", "member"[, ..., "memberN"]
          String groupName = record.get(0);
          log.info(() -> String.format("Adding group %s", groupName));
          // Parse the remaining columns as group memberships
          Supplier<Set<Membership>> members = new MembershipsSupplier(record);
          IdentityGroup group = context.buildIdentityGroup(groupName, members);
          groups.add(group);
        }
      }
      return new CheckpointCloseableIterableImpl.Builder<IdentityGroup>(groups)
          .setHasMore(false)
          .setCheckpoint((byte[])null)
          .build();
    }

    /**
     * Provides group memberships based on the CSV record.
     */
    private class MembershipsSupplier implements Supplier<Set<Membership>> {
      /** List of members from record */
      private List<String> membersList;

      /**
       * Extract group memberships from a CSV Record.
       *
       * @param record Current record
       */
      MembershipsSupplier(CSVRecord record) {
        this.membersList = StreamSupport.stream(record.spliterator(), false)
            .skip(1) // Skip group ID
            .collect(Collectors.toList());
      }

      /**
       * Provides a set of memberships based on the raw values
       * from the record. Each entry in the row may be a reference
       * to either a Google user, Google group (email-based group)
       * or one of the other groups referenced in the CSV file.
       *
       * Members are assumed to be email-based ids referencing Google users
       * or groups. To add a member that is another synced group,
       * prefix the group name with "gid:".
       *
       * @return
       */
      @Override
      public Set<Membership> get() {
        Set<Membership> members = new HashSet<>();
        // All memberships given 'member' role. Can be defined once as
        // static constant, defined here for illustrative purposes.
        MembershipRole memberRole = new MembershipRole().setName("MEMBER");
        List<MembershipRole> defaultRoles = Collections.singletonList(memberRole);

        for(String id: membersList) {
          EntityKey key;
          if (id.startsWith("gid:")) {
            // Treat name as another group
            String groupName = id.substring(4);
            key = context.buildEntityKeyForGroup(groupName);
          } else {
            // Treat name as a google user or email-based group
            key = new EntityKey().setId(id);
          }
          Membership membership = new Membership()
            .setMemberKey(key)
            .setRoles(defaultRoles);
          members.add(membership);
        }
        return members;
      }
    }

    /**
     * Clean up repository on shutdown. Not implemented.
     */
    @Override
    public void close() {

    }
  }
}
