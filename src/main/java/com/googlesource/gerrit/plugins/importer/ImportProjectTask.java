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

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;

class ImportProjectTask implements Runnable {

  interface Factory {
    ImportProjectTask create(String from, Project.NameKey name,
        CredentialsProvider cp, StringBuffer result);
  }

  private final GitRepositoryManager git;
  private final File lockRoot;
  private final Project.NameKey name;
  private final CredentialsProvider cp;
  private final String fromGerrit;
  private final StringBuffer logger;

  private Repository repo;

  @Inject
  ImportProjectTask(GitRepositoryManager git,
      @PluginData File data,
      @Assisted String fromGerrit,
      @Assisted Project.NameKey name,
      @Assisted CredentialsProvider cp,
      @Assisted StringBuffer logger) {
    this.git = git;
    this.lockRoot = data;
    this.name = name;
    this.cp = cp;
    this.fromGerrit = fromGerrit;
    this.logger = logger;
  }

  @Override
  public void run() {
    LockFile importing = lockForImport(name);
    if (importing == null) {
      return;
    }

    try {
      repo = openRepository();
      if (repo == null) {
        return;
      }

      try {
        setupProjectConfiguration();
        gitFetch();
      } catch(IOException | GitAPIException e) {
        logger.append(format("[ERROR] Unable to transfer project '%s' from"
            + " source gerrit host '%s': %s",
            name.get(), fromGerrit, e.getMessage()));
      } finally {
        repo.close();
      }
    } finally {
      importing.unlock();
      importing.commit();
    }
  }

  private LockFile lockForImport(Project.NameKey project) {
    File importStatus = new File(lockRoot, project.get());
    LockFile lockFile = new LockFile(importStatus, FS.DETECTED);
    try {
      if (lockFile.lock()) {
        return lockFile;
      } else {
        logger.append(format("Project %s is being imported from another session"
            + ", skipping", name.get()));
        return null;
      }
    } catch (IOException e1) {
      logger.append(format(
          "Error while trying to lock the project %s for import", name.get()));
      return null;
    }
  }

  private Repository openRepository() {
    try {
      git.openRepository(name);
      logger.append(format("Repository %s already exists.", name.get()));
      return null;
    } catch (RepositoryNotFoundException e) {
      // Project doesn't exist
    } catch (IOException e) {
      logger.append(e.getMessage());
      return null;
    }

    try {
      return git.createRepository(name);
    } catch(IOException e) {
      logger.append(format("Error: %s, skipping project %s", e, name.get()));
      return null;
    }
  }

  private void setupProjectConfiguration() throws IOException {
    StoredConfig config = repo.getConfig();
    config.setString("remote", "origin", "url", fromGerrit
        .concat("/")
        .concat(name.get()));
    config.setString("remote", "origin", "fetch", "+refs/*:refs/*");
    config.setString("http", null, "sslVerify", Boolean.FALSE.toString());
    config.save();
  }

  private void gitFetch() throws GitAPIException {
    FetchResult fetchResult;
    fetchResult = Git.wrap(repo).fetch()
          .setCredentialsProvider(cp)
          .setRemote("origin")
          .call();
    logger.append(format("[INFO] Project '%s' fetched: %s",
        name.get(), fetchResult.getMessages()));
  }
}
