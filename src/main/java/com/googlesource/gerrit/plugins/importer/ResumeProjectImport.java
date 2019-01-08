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

import static com.google.gerrit.server.permissions.GlobalPermission.ADMINISTRATE_SERVER;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.importer.ResumeProjectImport.Input;
import java.io.IOException;
import java.io.Writer;

@RequiresCapability(ImportCapability.ID)
public class ResumeProjectImport implements RestModifyView<ImportProjectResource, Input> {
  public static class Input {
    public String user;
    public String pass;
    public boolean force;

    private void validateResumeImport() throws BadRequestException {
      if (Strings.isNullOrEmpty(user)) {
        throw new BadRequestException("user is required");
      }
      if (Strings.isNullOrEmpty(pass)) {
        throw new BadRequestException("pass is required");
      }
    }

    private void validateResumeCopy() throws BadRequestException {
      user = Strings.emptyToNull(user);
      pass = Strings.emptyToNull(pass);
      if (user != null) {
        throw new BadRequestException("user must not be set");
      }
      if (pass != null) {
        throw new BadRequestException("pass must not be set");
      }
    }
  }

  private final ImportProject importProject;

  private boolean copy;
  private Writer err;

  @Inject
  public ResumeProjectImport(ImportProject importProject) {
    this.importProject = importProject;
  }

  ResumeProjectImport setCopy(boolean copy) {
    this.copy = copy;
    return this;
  }

  ResumeProjectImport setErr(Writer err) {
    this.err = err;
    return this;
  }

  @Override
  public ResumeImportStatistic apply(ImportProjectResource rsrc, Input input) throws Exception {
    if (copy) {
      input.validateResumeCopy();
    } else {
      input.validateResumeImport();
    }

    return importProjectFactory
        .create(rsrc.getName())
        .setCopy(copy)
        .setErr(err)
        .resume(input.user, input.pass, input.force, rsrc.getImportStatus());
  }

  public static class OnProjects
      implements RestModifyView<ProjectResource, Input>, UiAction<ProjectResource> {
    private final ProjectsCollection projectsCollection;
    private final ResumeProjectImport resumeProjectImport;
    private final Provider<CurrentUser> currentUserProvider;
    private final String pluginName;
    private final PermissionBackend permissionBackend;

    @Inject
    public OnProjects(
        ProjectsCollection projectsCollection,
        ResumeProjectImport resumeProjectImport,
        Provider<CurrentUser> currentUserProvider,
        @PluginName String pluginName,
        PermissionBackend permissionBackend) {
      this.projectsCollection = projectsCollection;
      this.resumeProjectImport = resumeProjectImport;
      this.currentUserProvider = currentUserProvider;
      this.pluginName = pluginName;
      this.permissionBackend = permissionBackend;
    }

    @Override
    public ResumeImportStatistic apply(ProjectResource rsrc, Input input) throws Exception {
      ImportProjectResource projectResource =
          projectsCollection.parse(new ConfigResource(), IdString.fromDecoded(rsrc.getName()));
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
      return permissionBackend.user(currentUserProvider.get()).testOrFalse(ADMINISTRATE_SERVER)
          || (permissionBackend
                  .user(currentUserProvider.get())
                  .testOrFalse(new PluginPermission(pluginName, ImportCapability.ID))
              && rsrc.getControl().isOwner());
    }

    private boolean isImported(ProjectResource rsrc) {
      try {
        ImportProjectResource projectResource =
            projectsCollection.parse(new ConfigResource(), IdString.fromDecoded(rsrc.getName()));
        ImportProjectInfo info = projectResource.getInfo();
        if (info.from == null) {
          // no import, but a copy within the same system
          return false;
        }

        return true;
      } catch (ResourceNotFoundException | IOException e) {
        return false;
      }
    }
  }
}
