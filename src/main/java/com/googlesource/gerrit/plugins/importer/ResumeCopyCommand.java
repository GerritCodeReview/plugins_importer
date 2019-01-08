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
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@RequiresCapability(CopyProjectCapability.ID)
@CommandMetaData(name = "resume-copy", description = "Resumes project copy")
public class ResumeCopyCommand extends SshCommand {

  @Option(name = "--force", usage = "Whether the resume should be done forcefully.")
  private boolean force;

  @Option(name = "--quiet", usage = "suppress progress messages")
  private boolean quiet;

  @Argument(index = 0, required = true, metaVar = "NAME", usage = "name of the target project")
  private String project;

  @Inject private ResumeCopyProject resume;

  @Inject private ProjectsCollection projects;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    try {
      ProjectResource rsrc =
          projects.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(project));
      if (!quiet) {
        resume.setErr(stderr);
      }
      ResumeCopyProject.Input input = new ResumeCopyProject.Input();
      input.force = force;
      resume.apply(rsrc, input);
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }
}
