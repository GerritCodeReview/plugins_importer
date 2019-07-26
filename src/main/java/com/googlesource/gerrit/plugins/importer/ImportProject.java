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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.importer.GerritApi.Version;
import com.googlesource.gerrit.plugins.importer.ImportProject.Input;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

import static com.googlesource.gerrit.plugins.importer.ProgressMonitorUtil.updateAndEnd;
import static java.lang.String.format;

@RequiresCapability(ImportCapability.ID)
class ImportProject
    implements RestCollectionCreateView<ConfigResource, ImportProjectResource, Input> {
  public static class Input {
    public String from;
    public String name;
    public String user;
    public String pass;
    public String parent;

    private void validateImport() throws BadRequestException {
      if (Strings.isNullOrEmpty(from)) {
        throw new BadRequestException("from is required");
      }
      if (Strings.isNullOrEmpty(user)) {
        throw new BadRequestException("user is required");
      }
      if (Strings.isNullOrEmpty(pass)) {
        throw new BadRequestException("pass is required");
      }
    }

    private void validateCopy() throws BadRequestException {
      from = Strings.emptyToNull(from);
      user = Strings.emptyToNull(user);
      pass = Strings.emptyToNull(pass);
      if (from != null) {
        throw new BadRequestException("from must not be set");
      }
      if (user != null) {
        throw new BadRequestException("user must not be set");
      }
      if (pass != null) {
        throw new BadRequestException("pass must not be set");
      }
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("from", from)
          .add("name", name)
          .add("user", user)
          .add("pass", PasswordUtil.mask(pass))
          .add("parent", parent)
          .toString();
    }
  }

  private static Logger log = LoggerFactory.getLogger(ImportProject.class);
  private static Version v2_11_2 = new Version("2.11.2");

  private final ProjectCache projectCache;
  private final OpenRepositoryStep openRepoStep;
  private final ConfigureRepositoryStep configRepoStep;
  private final GitFetchStep gitFetchStep;
  private final ConfigureProjectStep configProjectStep;
  private final ReplayChangesStep.Factory replayChangesFactory;
  private final ImportGroupsStep.Factory importGroupsStepFactory;
  private final GerritApi.Factory apiFactory;
  private final Provider<CurrentUser> currentUser;
  private final ImportJson importJson;
  private final ImportLog importLog;
  private final ProjectsCollection projects;

  private Project.NameKey srcProject;
  private Project.NameKey targetProject;
  private Project.NameKey parent;
  private boolean force;
  private GerritApi api;

  private boolean copy;
  private Writer err;

  @Inject
  ImportProject(
      ProjectCache projectCache,
      OpenRepositoryStep openRepoStep,
      ConfigureRepositoryStep configRepoStep,
      GitFetchStep gitFetchStep,
      ConfigureProjectStep configProjectStep,
      ReplayChangesStep.Factory replayChangesFactory,
      ImportGroupsStep.Factory importGroupsStepFactory,
      GerritApi.Factory apiFactory,
      Provider<CurrentUser> currentUser,
      ImportJson importJson,
      ImportLog importLog,
      ProjectsCollection projects) {
    this.projectCache = projectCache;
    this.openRepoStep = openRepoStep;
    this.configRepoStep = configRepoStep;
    this.gitFetchStep = gitFetchStep;
    this.configProjectStep = configProjectStep;
    this.replayChangesFactory = replayChangesFactory;
    this.importGroupsStepFactory = importGroupsStepFactory;
    this.apiFactory = apiFactory;
    this.currentUser = currentUser;
    this.importJson = importJson;
    this.importLog = importLog;
    this.projects = projects;
  }

  ImportProject setCopy(boolean copy) {
    this.copy = copy;
    return this;
  }

  ImportProject setErr(Writer err) {
    this.err = err;
    return this;
  }

  @Override
  public ImportStatistic apply(ConfigResource parentResource, IdString id, Input input)
      throws Exception {
    if (input == null) {
      input = new Input();
    }
    targetProject = new Project.NameKey(id.get());
    log.info("Importing project {}...", targetProject);
    log.debug("Input: {}", input);
    if (input.name == null) {
      input.name = id.get();
    }
    LockFile lockFile = lockForImport(id.get());
    try {
      return apply(lockFile, input, null);
    } finally {
      lockFile.unlock();
    }
  }

  public ResumeImportStatistic resume(
      String user,
      String pass,
      boolean force,
      Project.NameKey targetProject,
      File importStatus
  ) throws Exception {
    log.info("Resuming project import {}...", targetProject);
    this.targetProject = targetProject;
    ImportProjectInfo info = ImportJson.parse(importStatus);
    log.debug("ImportProjectInfo: {}", info);
    LockFile lockFile = lockForImport(info.from);
    try {
      ImportProject.Input input = new ImportProject.Input();
      input.user = user;
      input.pass = pass;
      input.from = info.from;
      input.name = info.name;
      input.parent = info.parent;

      this.force = force;

      return apply(lockFile, input, info);
    } finally {
      lockFile.unlock();
    }
  }

  private ResumeImportStatistic apply(LockFile lockFile, Input input, ImportProjectInfo info)
      throws Exception {
    boolean resume = info != null;
    api = apiFactory.create(input.from, input.user, input.pass);

    if (copy) {
      input.validateCopy();
    } else {
      input.validateImport();
      Version v = api.getVersion();
      if (v.compareTo(v2_11_2) < 0) {
        throw new BadRequestException(
            String.format(
                "The version of the source Gerrit server %s is too old. "
                    + "Its version is %s, but required is a version >= %s.",
                input.from, v.formatted, v2_11_2));
      }
    }

    ProgressMonitor pm = err != null ? new TextProgressMonitor(err) : NullProgressMonitor.INSTANCE;

    ResumeImportStatistic statistic = new ResumeImportStatistic();
    srcProject = new Project.NameKey(input.name);
    try {
      checkProjectInSource(pm);
      setParentProjectName(input, pm);
      checkPreconditions(pm);
      try (Repository repo = openRepoStep.open(targetProject, resume, pm, parent)) {
        ImportJson.persist(lockFile, importJson.format(input, info), pm);
        configRepoStep.configure(repo, srcProject, input.from, pm);
        gitFetchStep.fetch(input.user, input.pass, repo, pm);
        configProjectStep.configure(targetProject, parent, pm);
        replayChangesFactory
            .create(input.from, api, repo, srcProject, targetProject, force, resume, statistic, pm)
            .replay();
        if (!copy) {
          importGroupsStepFactory
              .create(input.from, input.user, input.pass, targetProject, pm)
              .importGroups();
        }
      }
      importLog.onImport((IdentifiedUser) currentUser.get(), srcProject, targetProject, input.from);
    } catch (BadRequestException e) {
      throw e;
    } catch (Exception e) {
      importLog.onImport(
          (IdentifiedUser) currentUser.get(), srcProject, targetProject, input.from, e);
      String msg =
          input.from != null
              ? format(
                  "Unable to transfer project '%s' from" + " source gerrit host '%s'.",
                  srcProject.get(), input.from)
              : format("Unable to copy project '%s'.", srcProject.get());
      log.error(msg, e);
      throw e;
    }

    return statistic;
  }

  private void checkProjectInSource(ProgressMonitor pm) throws IOException, BadRequestException {
    pm.beginTask("Check source project", 1);
    api.getProject(srcProject.get());
    updateAndEnd(pm);
  }

  private void setParentProjectName(Input input, ProgressMonitor pm)
      throws IOException, BadRequestException {
    pm.beginTask("Set parent project", 1);
    if (parent == null) {
      if (!Strings.isNullOrEmpty(input.parent)) {
        parent = new Project.NameKey(input.parent);
      } else {
        parent = new Project.NameKey(api.getProject(srcProject.get()).parent);
      }
    }
    updateAndEnd(pm);
  }

  private void checkPreconditions(ProgressMonitor pm) throws BadRequestException {
    pm.beginTask("Check preconditions", 1);
    if (parent == null) {
      throw new BadRequestException(
          "The project has no parent in the source system. "
              + "It can only be imported if a parent project is specified.");
    }
    ProjectState p = projectCache.get(parent);
    if (p == null) {
      throw new BadRequestException(
          format("Parent project '%s' does not exist in target.", parent.get()));
    }
    updateAndEnd(pm);
  }

  private LockFile lockForImport(String id) throws ResourceConflictException {
    File importStatus = projects.FS_LAYOUT.getImportStatusFile(id);
    LockFile lockFile = new LockFile(importStatus);
    try {
      if (lockFile.lock()) {
        return lockFile;
      }
      throw new ResourceConflictException("project is being imported from another session");
    } catch (IOException e1) {
      throw new ResourceConflictException("failed to lock project for import");
    }
  }
}
