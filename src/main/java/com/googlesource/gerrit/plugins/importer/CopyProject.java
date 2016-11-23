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
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import com.googlesource.gerrit.plugins.importer.CopyProject.Input;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

@RequiresCapability(CopyProjectCapability.ID)
class CopyProject implements RestModifyView<ProjectResource, Input>,
    UiAction<ProjectResource> {
  public static class Input {
    public String name;
  }

  private final ImportProject.Factory importProjectFactory;
  private final Provider<CurrentUser> currentUserProvider;
  private final String pluginName;
  private final PermissionBackend permissionBackend;
  private Writer err;

  @Inject
  CopyProject(
      ImportProject.Factory importProjectFactory,
      Provider<CurrentUser> currentUserProvider,
      @PluginName String pluginName,
      PermissionBackend permissionBackend) {
    this.importProjectFactory = importProjectFactory;
    this.currentUserProvider = currentUserProvider;
    this.pluginName = pluginName;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public ImportStatistic apply(ProjectResource rsrc, Input input)
      throws RestApiException, OrmException, IOException, ValidationException,
      GitAPIException, NoSuchChangeException, NoSuchAccountException,
      UpdateException, ConfigInvalidException, PermissionBackendException {
    if (Strings.isNullOrEmpty(input.name)) {
      throw new BadRequestException("name is required");
    }

    ImportProject.Input in = new ImportProject.Input();
    in.name = rsrc.getName();

    return importProjectFactory.create(new Project.NameKey(input.name))
        .setCopy(true)
        .setErr(err)
        .apply(new ConfigResource(), in);
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Copy...")
        .setTitle(String.format("Copy project %s", rsrc.getName()))
        .setVisible(canCopy());
  }

  private boolean canCopy() {
    return permissionBackend.user(currentUserProvider).testOrFalse(ADMINISTRATE_SERVER) ||
      permissionBackend.user(currentUserProvider).testOrFalse(
      new PluginPermission(pluginName, CopyProjectCapability.ID));
  }

  void setErr(PrintWriter err) {
    this.err = err;
  }
}
