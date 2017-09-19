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

import static com.googlesource.gerrit.plugins.importer.ProgressMonitorUtil.updateAndEnd;
import static java.lang.String.format;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

@Singleton
class OpenRepositoryStep {
  private final GitRepositoryManager git;
  private final ProjectCache projectCache;
  private final DynamicSet<ProjectCreationValidationListener>
      projectCreationValidationListeners;
  private final Provider<ProjectsCollection> projectsCollection;

  @Inject
  OpenRepositoryStep(GitRepositoryManager git,
      ProjectCache projectCache,
      DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners,
      Provider<ProjectsCollection> projectsCollection) {
    this.git = git;
    this.projectCache = projectCache;
    this.projectCreationValidationListeners = projectCreationValidationListeners;
    this.projectsCollection = projectsCollection;
  }

  Repository open(Project.NameKey name, boolean resume, ProgressMonitor pm,
      Project.NameKey parent) throws ResourceConflictException, IOException,
      UnprocessableEntityException, PermissionBackendException {
    pm.beginTask("Open repository", 1);
    try {
      Repository repo = git.openRepository(name);
      if (resume) {
        return repo;
      }
      throw new ResourceConflictException(format(
          "repository %s already exists", name.get()));
    } catch (RepositoryNotFoundException e) {
      // Project doesn't exist
      if (resume) {
        throw new ResourceConflictException(format(
            "repository %s does not exist", name.get()));
      }
    }

    beforeCreateProject(name, parent);
    Repository repo = git.createRepository(name);
    onProjectCreated(name);
    updateAndEnd(pm);
    return repo;
  }

  private void beforeCreateProject(Project.NameKey name, Project.NameKey parent)
      throws ResourceConflictException, UnprocessableEntityException,
      IOException, PermissionBackendException {
    CreateProjectArgs args = new CreateProjectArgs();
    args.setProjectName(name);
    args.newParent = projectsCollection.get().parse(parent.get()).getNameKey();
    for (ProjectCreationValidationListener l : projectCreationValidationListeners) {
      try {
        l.validateNewProject(args);
      } catch (ValidationException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }
    }
  }

  private void onProjectCreated(Project.NameKey name) {
    projectCache.onCreateProject(name);
  }
}
