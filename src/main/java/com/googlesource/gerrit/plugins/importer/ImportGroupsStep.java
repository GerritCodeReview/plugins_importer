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

import static com.google.gerrit.reviewdb.client.AccountGroup.isInternalGroup;

import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ProgressMonitor;

public class ImportGroupsStep {

  interface Factory {
    ImportGroupsStep create(
        @Assisted("from") String fromGerrit,
        @Assisted("user") String user,
        @Assisted("password") String password,
        Project.NameKey project,
        ProgressMonitor pm);
  }

  private final ProjectCache projectCache;
  private final GroupCache groupCache;
  private final ImportGroup.Factory importGroupFactory;
  private final String fromGerrit;
  private final String user;
  private final String password;
  private final Project.NameKey project;
  private final ProgressMonitor pm;

  @Inject
  ImportGroupsStep(
      ProjectCache projectCache,
      GroupCache groupCache,
      ImportGroup.Factory importGroupFactory,
      @Assisted("from") String fromGerrit,
      @Assisted("user") String user,
      @Assisted("password") String password,
      @Assisted Project.NameKey project,
      @Assisted ProgressMonitor pm) {
    this.projectCache = projectCache;
    this.groupCache = groupCache;
    this.importGroupFactory = importGroupFactory;
    this.fromGerrit = fromGerrit;
    this.user = user;
    this.password = password;
    this.project = project;
    this.pm = pm;
  }

  void importGroups()
      throws NoSuchAccountException, OrmException, IOException, RestApiException,
          ConfigInvalidException {
    ProjectConfig projectConfig = projectCache.get(project).getConfig();
    Set<AccountGroup.UUID> groupUUIDs = projectConfig.getAllGroupUUIDs();
    pm.beginTask("Import Groups", groupUUIDs.size());
    for (AccountGroup.UUID groupUUID : groupUUIDs) {
      if (isInternalGroup(groupUUID)) {
        if (groupCache.get(groupUUID) == null) {
          ImportGroup.Input input = new ImportGroup.Input();
          input.from = fromGerrit;
          input.user = user;
          input.pass = password;
          input.importOwnerGroup = true;
          input.importIncludedGroups = true;
          try {
            importGroupFactory
                .create(new AccountGroup.NameKey(projectConfig.getGroup(groupUUID).getName()))
                .apply(new ConfigResource(), input);
          } catch (ResourceConflictException | MethodNotAllowedException e) {
            // should not happen
            throw new IllegalStateException(e);
          }
        }
      }
      pm.update(1);
    }
    pm.endTask();
  }
}
