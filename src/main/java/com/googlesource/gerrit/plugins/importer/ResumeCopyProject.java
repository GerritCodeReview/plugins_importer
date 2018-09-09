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
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.importer.ResumeCopyProject.Input;
import java.io.IOException;
import java.io.Writer;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(CopyProjectCapability.ID)
class ResumeCopyProject
    implements RestModifyView<ProjectResource, Input>, UiAction<ProjectResource> {
  public static class Input {
    public boolean force;
  }

  private final Provider<ResumeProjectImport> resumeProjectImport;
  private final ProjectsCollection projectsCollection;
  private final Provider<CurrentUser> currentUserProvider;
  private final String pluginName;
  private final ProjectCache projectCache;

  private Writer err;

  @Inject
  ResumeCopyProject(
      Provider<ResumeProjectImport> resumeProjectImport,
      ProjectsCollection projectsCollection,
      Provider<CurrentUser> currentUserProvider,
      @PluginName String pluginName,
      ProjectCache projectCache) {
    this.resumeProjectImport = resumeProjectImport;
    this.projectsCollection = projectsCollection;
    this.currentUserProvider = currentUserProvider;
    this.pluginName = pluginName;
    this.projectCache = projectCache;
  }

  ResumeCopyProject setErr(Writer err) {
    this.err = err;
    return this;
  }

  @Override
  public ResumeImportStatistic apply(ProjectResource rsrc, Input input)
      throws RestApiException, IOException, OrmException, ValidationException, GitAPIException,
          NoSuchChangeException, NoSuchAccountException, UpdateException, ConfigInvalidException {
    ImportProjectResource projectResource =
        projectsCollection.parse(new ConfigResource(), IdString.fromDecoded(rsrc.getName()));
    ResumeProjectImport.Input in = new ResumeProjectImport.Input();
    in.force = input.force;
    return resumeProjectImport.get().setCopy(true).setErr(err).apply(projectResource, in);
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Resume Copy...")
        .setTitle(String.format("Resume copy for project %s", rsrc.getName()))
        .setVisible(canResumeCopy(rsrc) && isCopied(rsrc));
  }

  private boolean canResumeCopy(ProjectResource rsrc) {
    CapabilityControl ctl = currentUserProvider.get().getCapabilities();
    return ctl.canAdministrateServer()
        || (ctl.canPerform(pluginName + "-" + CopyProjectCapability.ID)
            && rsrc.getControl().isOwner());
  }

  private boolean isCopied(ProjectResource rsrc) {
    try {
      ImportProjectResource projectResource =
          projectsCollection.parse(new ConfigResource(), IdString.fromDecoded(rsrc.getName()));
      ImportProjectInfo info = projectResource.getInfo();
      if (info.from != null) {
        // no copy, but an import from another system
        return false;
      }

      // check that source project still exists
      return projectCache.get(new Project.NameKey(info.name)) != null;
    } catch (ResourceNotFoundException | IOException e) {
      return false;
    }
  }
}
