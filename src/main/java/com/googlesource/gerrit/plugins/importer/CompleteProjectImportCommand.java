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

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Argument;

import java.io.IOException;

@RequiresCapability(ImportCapability.ID)
@CommandMetaData(name = "complete-project", description = "Completes project import")
class CompleteProjectImportCommand extends SshCommand {

  @Argument(index = 0, required = true, metaVar = "NAME",
      usage = "name of the project in target system")
  private String project;

  @Inject
  private CompleteProjectImport completeProjectImport;

  @Inject
  private ProjectsCollection projects;

  @Override
  protected void run() throws UnloggedFailure, RepositoryNotFoundException,
      IOException {
    try {
      ImportProjectResource rsrc = projects.parse(project);
      completeProjectImport.apply(rsrc, new CompleteProjectImport.Input());
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }
}
