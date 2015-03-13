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

import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import com.googlesource.gerrit.plugins.importer.CopyProject.Input;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;

@Singleton
@RequiresCapability(CopyProjectCapability.ID)
class ResumeCopyProject implements RestModifyView<ProjectResource, Input> {
  private final ResumeProjectImport resumeProjectImport;
  private final ProjectsCollection projectsCollection;
  private final Provider<CurrentUser> currentUserProvider;

  @Inject
  ResumeCopyProject(
      ResumeProjectImport resumeProjectImport,
      ProjectsCollection projectsCollection,
      Provider<CurrentUser> currentUserProvider) {
    this.resumeProjectImport = resumeProjectImport;
    this.projectsCollection = projectsCollection;
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  public Response<String> apply(ProjectResource rsrc, Input input)
      throws RestApiException, IOException, OrmException, ValidationException,
      GitAPIException, NoSuchChangeException, NoSuchAccountException {
    AccountState s = ((IdentifiedUser) currentUserProvider.get()).state();

    ResumeProjectImport.Input in = new ResumeProjectImport.Input();
    in.user = s.getUserName();
    in.pass = s.getPassword(s.getUserName());

    ImportProjectResource projectResource =
        projectsCollection.parse(new ConfigResource(),
            IdString.fromDecoded(rsrc.getName()));
    return resumeProjectImport.apply(projectResource, in);
  }
}
