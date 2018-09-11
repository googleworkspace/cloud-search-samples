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

import com.beust.jcommander.JCommander;

import java.util.HashMap;
import java.util.Map;

/**
 * Main entry point for the sample. Allows running individual commands from
 * the CLI.
 */
public class Main {
  public static void main(String[] args) {
    Map<String, Runnable> commands = new HashMap<>();
    commands.put("create-group", new CreateGroupCommand());
    commands.put("get-group", new GetGroupCommand());
    commands.put("delete-group", new DeleteGroupCommand());
    commands.put("search-groups", new SearchGroupsCommand());
    commands.put("insert-member", new InsertMemberCommand());
    commands.put("remove-member", new RemoveMemberCommand());
    commands.put("list-members", new ListMembersCommand());
    commands.put("map-user-identity", new MapUserIdentityCommand());
    commands.put("unmap-user-identity", new UnmapUserIdentityCommand());
    commands.put("get-user", new GetUserCommand());

    JCommander.Builder builder = JCommander.newBuilder();
    commands.forEach((s, command) -> builder.addCommand(s, command));

    JCommander parser = builder.build();
    parser.parse(args);

    String command = parser.getParsedCommand();
    if (command == null) {
      parser.usage();
      System.exit(1);
    }
    commands.get(command).run();
  }
}