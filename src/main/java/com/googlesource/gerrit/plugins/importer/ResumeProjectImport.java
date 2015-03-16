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
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.googlesource.gerrit.plugins.importer.ResumeProjectImport.Input;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;

@Singleton
@RequiresCapability(ImportCapability.ID)
public class ResumeProjectImport implements RestModifyView<ImportProjectResource, Input> {
  public static class Input {
    public String user;
    public String pass;
  }

  private final ImportProject.Factory importProjectFactory;

  @Inject
  public ResumeProjectImport(ImportProject.Factory importProjectFactory) {
    this.importProjectFactory = importProjectFactory;
  }

  @Override
  public Response<String> apply(ImportProjectResource rsrc, Input input)
      throws RestApiException, IOException, OrmException, ValidationException,
      GitAPIException, NoSuchChangeException, NoSuchAccountException {
    if (Strings.isNullOrEmpty(input.user)) {
      throw new BadRequestException("user is required");
    }
    if (Strings.isNullOrEmpty(input.pass)) {
      throw new BadRequestException("pass is required");
    }
    return importProjectFactory.create(rsrc.getName())
        .resume(input.user, input.pass, rsrc.getImportStatus());
  }

  public static class OnProjects implements RestModifyView<ProjectResource, Input> {
    private final ProjectsCollection projectsCollection;
    private final ResumeProjectImport resumeProjectImport;

    @Inject
    public OnProjects(
        ProjectsCollection projectsCollection,
        ResumeProjectImport resumeProjectImport) {
      this.projectsCollection = projectsCollection;
      this.resumeProjectImport = resumeProjectImport;
    }

    @Override
    public Response<String> apply(ProjectResource rsrc, Input input)
        throws RestApiException, IOException, OrmException,
        ValidationException, GitAPIException, NoSuchChangeException,
        NoSuchAccountException {
      ImportProjectResource projectResource =
          projectsCollection.parse(new ConfigResource(),
              IdString.fromDecoded(rsrc.getName()));
      return resumeProjectImport.apply(projectResource, input);
    }
  }
}
