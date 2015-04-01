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

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@RequiresCapability(CopyProjectCapability.ID)
@CommandMetaData(name = "copy-project", description = "Copies a project")
public class CopyProjectCommand extends SshCommand {

  @Option(name = "--quiet", usage = "suppress progress messages")
  private boolean quiet;

  @Argument(index = 0, required = true, metaVar = "NAME",
      usage = "name of the source project")
  private String source;

  @Argument(index = 1, required = true, metaVar = "COPY",
      usage = "name of the project copy")
  private String target;

  @Inject
  private ProjectsCollection projects;

  @Inject
  private CopyProject copy;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    try {
      ProjectResource srcProject = projects.parse(source);
      if (!quiet) {
        copy.setErr(stderr);
      }
      CopyProject.Input input = new CopyProject.Input();
      input.name = target;
      ImportStatistic stats = copy.apply(srcProject, input);
      stdout.print("Created Changes: " + stats.numChangesCreated + "\n");
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }
}
