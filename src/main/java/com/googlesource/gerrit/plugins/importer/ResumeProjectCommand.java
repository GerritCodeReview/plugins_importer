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
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@RequiresCapability(ImportCapability.ID)
@CommandMetaData(name = "resume-project", description = "Resumes project import")
public class ResumeProjectCommand extends SshCommand {

  @Option(
    name = "--user",
    aliases = {"-u"},
    required = true,
    metaVar = "NAME",
    usage = "user on remote system"
  )
  private String user;

  @Option(
    name = "--pass",
    aliases = {"-p"},
    required = true,
    metaVar = "-|PASS",
    usage = "password of remote user"
  )
  private String pass;

  @Option(name = "--force", usage = "Whether the resume should be done forcefully.")
  private boolean force;

  @Option(name = "--quiet", usage = "suppress progress messages")
  private boolean quiet;

  @Argument(
    index = 0,
    required = true,
    metaVar = "NAME",
    usage = "name of the project in target system"
  )
  private String project;

  @Inject private ResumeProjectImport resume;

  @Inject private ProjectsCollection projects;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    try {
      ImportProjectResource rsrc = projects.parse(project);
      if (!quiet) {
        resume.setErr(stderr);
      }
      ResumeProjectImport.Input input = new ResumeProjectImport.Input();
      input.user = user;
      input.pass = PasswordUtil.readPassword(in, pass);
      input.force = force;
      ResumeImportStatistic stats = resume.apply(rsrc, input);
      stdout.print("Created Changes: " + stats.numChangesCreated + "\n");
      stdout.print("Updated Changes: " + stats.numChangesUpdated + "\n");
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }
}
