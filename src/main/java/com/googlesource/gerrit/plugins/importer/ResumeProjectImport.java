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
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import com.googlesource.gerrit.plugins.importer.ResumeProjectImport.Input;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.io.Writer;

@RequiresCapability(ImportCapability.ID)
public class ResumeProjectImport implements RestModifyView<ImportProjectResource, Input> {
  public static class Input {
    public String user;
    public String pass;
    public boolean force;
  }

  private final ImportProject.Factory importProjectFactory;

  private Writer err;

  @Inject
  public ResumeProjectImport(ImportProject.Factory importProjectFactory) {
    this.importProjectFactory = importProjectFactory;
  }

  @Override
  public ResumeImportStatistic apply(ImportProjectResource rsrc, Input input)
      throws RestApiException, IOException, OrmException, ValidationException,
      GitAPIException, NoSuchChangeException, NoSuchAccountException {
    if (Strings.isNullOrEmpty(input.user)) {
      throw new BadRequestException("user is required");
    }
    if (Strings.isNullOrEmpty(input.pass)) {
      throw new BadRequestException("pass is required");
    }

    ImportProject importer = importProjectFactory.create(rsrc.getName());
    importer.setErr(err);
    return importer.resume(input.user, input.pass, input.force,
        rsrc.getImportStatus());
  }

  public static class OnProjects implements
      RestModifyView<ProjectResource, Input>, UiAction<ProjectResource> {
    private final ProjectsCollection projectsCollection;
    private final ResumeProjectImport resumeProjectImport;
    private final Provider<CurrentUser> currentUserProvider;
    private final String pluginName;
    private final String canonicalWebUrl;

    @Inject
    public OnProjects(
        ProjectsCollection projectsCollection,
        ResumeProjectImport resumeProjectImport,
        Provider<CurrentUser> currentUserProvider,
        @PluginName String pluginName,
        @CanonicalWebUrl String canonicalWebUrl) {
      this.projectsCollection = projectsCollection;
      this.resumeProjectImport = resumeProjectImport;
      this.currentUserProvider = currentUserProvider;
      this.pluginName = pluginName;
      this.canonicalWebUrl = canonicalWebUrl;
    }

    @Override
    public ResumeImportStatistic apply(ProjectResource rsrc, Input input)
        throws RestApiException, IOException, OrmException,
        ValidationException, GitAPIException, NoSuchChangeException,
        NoSuchAccountException {
      ImportProjectResource projectResource =
          projectsCollection.parse(new ConfigResource(),
              IdString.fromDecoded(rsrc.getName()));
      return resumeProjectImport.apply(projectResource, input);
    }

    @Override
    public UiAction.Description getDescription(ProjectResource rsrc) {
      return new UiAction.Description()
          .setLabel("Resume Import...")
          .setTitle(String.format("Resume import for project %s", rsrc.getName()))
          .setVisible(canResumeImport(rsrc) && isImported(rsrc));
    }

    private boolean canResumeImport(ProjectResource rsrc) {
      CapabilityControl ctl = currentUserProvider.get().getCapabilities();
      return ctl.canAdministrateServer()
          || (ctl.canPerform(pluginName + "-" + ImportCapability.ID)
              && rsrc.getControl().isOwner());
    }

    private boolean isImported(ProjectResource rsrc) {
      try {
        ImportProjectResource projectResource =
            projectsCollection.parse(new ConfigResource(),
                IdString.fromDecoded(rsrc.getName()));
        ImportProjectInfo info = projectResource.getInfo();
        if (canonicalWebUrl.equals(info.from)) {
          // no import, but a copy within the same system
          return false;
        }

        return true;
      } catch (ResourceNotFoundException | IOException e) {
        return false;
      }
    }
  }

  void setErr(Writer err) {
    this.err = err;
  }
}
