// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import static java.lang.String.format;

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@RequiresCapability(ImportCapability.ID)
@CommandMetaData(name = "list-projects", description = "Lists project imports")
class ListProjectImportsCommand extends SshCommand {

  @Option(
      name = "--verbose",
      aliases = {"-v"},
      required = false,
      usage = "Print detailed info for each project import")
  private boolean verbose;

  @Argument(
      index = 0,
      required = false,
      metaVar = "MATCH",
      usage = "List only projects containing this substring, case insensitive")
  private String match;

  @Inject private ListImportedProjects list;

  @Override
  protected void run() throws IOException {
    if (match != null) {
      list.setMatch(match);
    }

    Map<String, ImportProjectInfo> imports = list.apply(new ConfigResource());
    for (Map.Entry<String, ImportProjectInfo> e : imports.entrySet()) {
      stdout.println(e.getKey());
      if (verbose) {
        ImportProjectInfo info = e.getValue();
        stdout.println("  from: " + info.from);
        stdout.println("  parent: " + info.parent);
        stdout.println(format("  %-23s %s:%s", "time", "user", "remote-user"));
        for (ImportInfo i : info.imports) {
          stdout.println(format("  %-23s %s:%s", i.timestamp, i.user.username, i.remoteUser));
        }
        stdout.println();
      }
    }
  }
}
