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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;

import com.googlesource.gerrit.plugins.importer.CompleteProjectImport.Input;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RequiresCapability(ImportCapability.ID)
class CompleteProjectImport implements RestModifyView<ImportProjectResource, Input> {
  public static class Input {
  }

  private final ProjectsCollection projects;
  private final GitRepositoryManager repoManager;

  @Inject
  CompleteProjectImport(
      ProjectsCollection projects,
      GitRepositoryManager repoManager) {
    this.projects = projects;
    this.repoManager = repoManager;
  }

  @Override
  public Response<?> apply(ImportProjectResource rsrc, Input input)
      throws ResourceConflictException, RepositoryNotFoundException,
      IOException {
    LockFile lock = lockForDelete(rsrc.getName());
    try {
      deleteImportRefs(rsrc.getName());
      rsrc.getImportStatus().delete();
      return Response.none();
    } finally {
      lock.unlock();
    }
  }

  private LockFile lockForDelete(Project.NameKey project)
      throws ResourceConflictException {
    File importStatus = projects.FS_LAYOUT.getImportStatusFile(project.get());
    LockFile lockFile = new LockFile(importStatus);
    try {
      if (lockFile.lock()) {
        return lockFile;
      } else {
        throw new ResourceConflictException(
            "project is being imported from another session");
      }
    } catch (IOException e) {
      throw new ResourceConflictException("failed to lock project for delete");
    }
  }

  private void deleteImportRefs(Project.NameKey project)
      throws RepositoryNotFoundException, IOException {
    try (Repository repo = repoManager.openRepository(project)) {
      Map<String, Ref> refs = repo.getRefDatabase().getRefs(
          ConfigureRepositoryStep.R_IMPORTS);
      for (Ref ref : refs.values()) {
        RefUpdate ru = repo.updateRef(ref.getName());
        ru.setForceUpdate(true);
        RefUpdate.Result result = ru.delete();
        switch (result) {
          case NEW:
          case NO_CHANGE:
          case FAST_FORWARD:
          case FORCED:
            break;
          case IO_FAILURE:
          case LOCK_FAILURE:
          case NOT_ATTEMPTED:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case RENAMED:
          default:
            throw new IOException(String.format(
                "Failed to delete %s, RefUpdate.Result = %s", ref, result));
        }
      }
    }
  }

  public static class OnProjects implements
      RestModifyView<ProjectResource, Input>, UiAction<ProjectResource> {
    private final ProjectsCollection projectsCollection;
    private final CompleteProjectImport completeProjectImport;
    private final Provider<CurrentUser> currentUserProvider;
    private final String pluginName;

    @Inject
    public OnProjects(ProjectsCollection projectsCollection,
        CompleteProjectImport completeProjectImport,
        Provider<CurrentUser> currentUserProvider,
        @PluginName String pluginName) {
      this.projectsCollection = projectsCollection;
      this.completeProjectImport = completeProjectImport;
      this.currentUserProvider = currentUserProvider;
      this.pluginName = pluginName;
    }

    @Override
    public Response<?> apply(ProjectResource rsrc, Input input)
        throws ResourceNotFoundException, ResourceConflictException,
        RepositoryNotFoundException, IOException {
      ImportProjectResource projectResource =
          projectsCollection.parse(new ConfigResource(),
              IdString.fromDecoded(rsrc.getName()));
      return completeProjectImport.apply(projectResource, input);
    }

    @Override
    public UiAction.Description getDescription(ProjectResource rsrc) {
      UiAction.Description desc = new UiAction.Description();

      try {
        ImportProjectResource importRsrc = projectsCollection.parse(new ConfigResource(),
            IdString.fromDecoded(rsrc.getName()));
        if (importRsrc.getInfo().from != null) {
          desc.setLabel("Complete Import...")
              .setTitle("Complete the project import."
                  + " After completion, resume is not possible anymore.");
        } else {
          desc.setLabel("Complete Copy...")
              .setTitle("Complete the project copy."
                  + " After completion, resume is not possible anymore.");
        }
        desc.setVisible(canCompleteImport(rsrc));
      } catch (IOException | ResourceNotFoundException e) {
        desc.setVisible(false);
      }

      return desc;
    }

    private boolean canCompleteImport(ProjectResource rsrc) {
      CapabilityControl ctl = currentUserProvider.get().getCapabilities();
      return ctl.canAdministrateServer()
          || (ctl.canPerform(pluginName + "-" + ImportCapability.ID)
              && rsrc.getControl().isOwner());
    }
  }
}
