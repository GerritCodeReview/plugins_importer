//Copyright (C) 2015 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import static java.lang.String.format;

import com.google.common.base.Charsets;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

class ImportProjectTask implements Runnable {

  private static Logger log = LoggerFactory.getLogger(ImportProjectTask.class);

  interface Factory {
    ImportProjectTask create(
        @Assisted("from") String from,
        @Assisted Project.NameKey name,
        @Assisted("user") String user,
        @Assisted("password") String password,
        @Assisted StringBuffer result);
  }

  private final ProjectCache projectCache;
  private final OpenRepositoryStep openRepoStep;
  private final ConfigureRepositoryStep configRepoStep;
  private final GitFetchStep gitFetchStep;
  private final ReplayChangesStep.Factory replayChangesFactory;
  private final File lockRoot;

  private final String fromGerrit;
  private final Project.NameKey name;
  private final String user;
  private final String password;
  private final StringBuffer messages;

  private LockFile lockFile;

  @Inject
  ImportProjectTask(
      ProjectCache projectCache,
      OpenRepositoryStep openRepoStep,
      ConfigureRepositoryStep configRepoStep,
      GitFetchStep gitFetchStep,
      ReplayChangesStep.Factory replayChangesFactory,
      @PluginData File data,
      @Assisted("from") String fromGerrit,
      @Assisted Project.NameKey name,
      @Assisted("user") String user,
      @Assisted("password") String password,
      @Assisted StringBuffer messages) {
    this.projectCache = projectCache;
    this.openRepoStep = openRepoStep;
    this.configRepoStep = configRepoStep;
    this.gitFetchStep = gitFetchStep;
    this.replayChangesFactory = replayChangesFactory;
    this.lockRoot = data;

    this.fromGerrit = fromGerrit;
    this.name = name;
    this.user = user;
    this.password = password;
    this.messages = messages;
  }

  @Override
  public void run() {
    lockFile = lockForImport(name);
    if (lockFile == null) {
      return;
    }

    try {
      checkPreconditions();

      Repository repo = openRepoStep.open(name, messages);
      if (repo == null) {
        return;
      }

      try {
        persistParams();
        configRepoStep.configure(repo, name, fromGerrit);
        gitFetchStep.fetch(user, password, repo, name, messages);
        replayChangesFactory.create(fromGerrit, user, password, repo, name)
            .replay();
      } catch (IOException | GitAPIException | OrmException
          | NoSuchAccountException | NoSuchChangeException | RestApiException
          | ValidationException | RuntimeException e) {
        handleError(e);
      } finally {
        repo.close();
      }
    } catch (ResourceConflictException | IOException | ValidationException e) {
      handleError(e);
    } finally {
      lockFile.unlock();
    }
  }

  private void checkPreconditions() throws IOException, ValidationException {
    ProjectInfo p =
        new RemoteApi(fromGerrit, user, password).getProject(name.get());
    ProjectState parent = projectCache.get(new Project.NameKey(p.parent));
    if (parent == null) {
      throw new ValidationException(format(
          "Parent project %s does not exist in target,", p.parent));
    }
  }

  static class Params {
    String from;
    String user;
    String project;
  }

  private void persistParams() throws IOException {
    Params p = new Params();
    p.from = fromGerrit;
    p.user = user;
    p.project = name.get();

    String s = OutputFormat.JSON_COMPACT.newGson().toJson(p);
    try (OutputStream out = lockFile.getOutputStream()) {
      out.write(s.getBytes(Charsets.UTF_8));
      out.write('\n');
    } finally {
      lockFile.commit();
    }
  }

  private void handleError(Exception e) {
    messages.append(format("Unable to transfer project '%s' from"
        + " source gerrit host '%s': %s. Check log for details.",
        name.get(), fromGerrit, e.getMessage()));
    log.error(format("Unable to transfer project '%s' from"
        + " source gerrit host '%s'.",
        name.get(), fromGerrit), e);
  }

  private LockFile lockForImport(Project.NameKey project) {
    File importStatus = new File(lockRoot, project.get());
    LockFile lockFile = new LockFile(importStatus, FS.DETECTED);
    try {
      if (lockFile.lock()) {
        return lockFile;
      } else {
        messages.append(format("Project %s is being imported from another session"
            + ", skipping", name.get()));
        return null;
      }
    } catch (IOException e1) {
      messages.append(format(
          "Error while trying to lock the project %s for import", name.get()));
      return null;
    }
  }
}
