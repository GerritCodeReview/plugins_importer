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

import com.google.common.base.Strings;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;

@RequiresCapability(ImportCapability.ID)
@CommandMetaData(name = "project", description = "Imports a project")
public class ProjectCommand extends SshCommand {
  @Option(name = "--from", aliases = {"-f"}, required = true, metaVar = "URL",
      usage = "URL of the remote system from where the project should be imported")
  private String url;

  @Option(name = "--name", required = false, metaVar = "NAME",
      usage = "name of project in source system (if not specified it is"
          + " assumed to be the same name as in the target system)")
  private String name;

  @Option(name = "--user", aliases = {"-u"}, required = true, metaVar = "NAME",
      usage = "user on remote system")
  private String user;

  @Option(name = "--pass", aliases = {"-p"}, required = true, metaVar = "-|PASS",
      usage = "password of remote user")
  private String pass;

  @Option(name = "--parent", required = false, metaVar = "NAME",
      usage = "name of parent project in target system")
  private String parent;

  @Option(name = "--quiet", usage = "suppress progress messages")
  private boolean quiet;

  @Argument(index = 0, required = true, metaVar = "NAME",
      usage = "name of the project in target system")
  private String project;

  @Inject
  private ImportProject.Factory importProjectFactory;

  @Override
  protected void run()
      throws OrmException, IOException, UnloggedFailure, ValidationException,
      GitAPIException, NoSuchChangeException, NoSuchAccountException,
      UpdateException, ConfigInvalidException {
    ImportProject.Input input = new ImportProject.Input();
    input.from = url;
    input.name = name;
    input.user = user;
    input.pass = PasswordUtil.readPassword(in, pass);
    if (!Strings.isNullOrEmpty(parent)) {
      input.parent = parent;
    }

    Thread.currentThread().setName(
        Thread.currentThread().getName().replaceAll("\\s(--pass|-p)([\\s+\\=])\\S+", " $1$2***"));

    try {
      ImportProject importer = importProjectFactory.create(new Project.NameKey(project));
      if (!quiet) {
        importer.setErr(stderr);
      }
      ImportStatistic stats = importer.apply(new ConfigResource(), input);
      stdout.print("Created Changes: " + stats.numChangesCreated + "\n");
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }
}
