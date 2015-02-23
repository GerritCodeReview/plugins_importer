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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.googlesource.gerrit.plugins.importer.ProjectRestEndpoint.Input;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiresCapability(ImportCapability.ID)
@Singleton
class ProjectRestEndpoint implements RestModifyView<ConfigResource, Input>,
    LifecycleListener {
  public static class Input {
    public String from;
    public String user;
    public String pass;
    public List<String> projects;
  }

  private static class ImportProjectTask implements Runnable  {

    private final GitRepositoryManager git;
    private final File lockRoot;
    private final Project.NameKey name;
    private final CredentialsProvider cp;
    private final String fromGerrit;
    private final StringBuffer logger;

    ImportProjectTask(GitRepositoryManager git, File lockRoot,
        Project.NameKey name, CredentialsProvider cp, String fromGerrit,
      StringBuffer logger) {
      this.git = git;
      this.lockRoot = lockRoot;
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
        try {
          git.openRepository(name);
          logger.append(format("Repository %s already exists.", name.get()));
          return;
        } catch (RepositoryNotFoundException e) {
          // Ideal, project doesn't exist
        } catch (IOException e) {
          logger.append(e.getMessage());
          return;
        }

        Repository repo;
        try {
          repo = git.createRepository(name);
        } catch(IOException e) {
          logger.append(format("Error: %s, skipping project %s", e, name.get()));
          return;
        }

        try {
          setupProjectConfiguration(fromGerrit, name.get(), repo.getConfig());
          FetchResult fetchResult = Git.wrap(repo).fetch()
              .setCredentialsProvider(cp)
              .setRemote("origin")
              .call();
          logger.append(format("[INFO] Project '%s' imported: %s",
              name.get(), fetchResult.getMessages()));
        } catch(IOException | GitAPIException e) {
          logger.append(format("[ERROR] Unable to transfere project '%s' from"
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
  }

  // TODO: this should go into the plugin configuration.
  private final static int maxNumberOfImporterThreads = 20;

  private final GitRepositoryManager git;
  private final WorkQueue queue;
  private final File data;

  private WorkQueue.Executor executor;
  private ListeningExecutorService pool;

  @Inject
  ProjectRestEndpoint(GitRepositoryManager git, WorkQueue queue,
      @PluginData File data) {
    this.git = git;
    this.queue = queue;
    this.data = data;
  }

  @Override
  public String apply(ConfigResource rsrc, Input input) {

    long startTime = System.currentTimeMillis();
    StringBuffer result = new StringBuffer();
    CredentialsProvider cp =
        new UsernamePasswordCredentialsProvider(input.user, input.pass);

    List<ListenableFuture<?>> tasks = new ArrayList<>();
    for(String projectName : input.projects) {
      Project.NameKey name = new Project.NameKey(projectName);
      Runnable task = new ImportProjectTask(
          git, data, name, cp, input.from, result);
      tasks.add(pool.submit(task));
    }
    Futures.getUnchecked(Futures.allAsList(tasks));
    // TODO: the log message below does not take the failed imports into account.
    result.append(format("[INFO] %d projects imported in %d milliseconds.%n",
        input.projects.size(), (System.currentTimeMillis() - startTime)));
    return result.toString();
  }

  @Override
  public void start() {
    executor = queue.createQueue(maxNumberOfImporterThreads, "ProjectImporter");
    pool = MoreExecutors.listeningDecorator(executor);
  }

  @Override
  public void stop() {
    if (executor != null) {
      executor.shutdown();
      executor.unregisterWorkQueue();
      executor = null;
      pool = null;
    }
  }

  private static void setupProjectConfiguration(String sourceGerritServerUrl,
      String projectName, StoredConfig config) throws IOException {
    config.setString("remote", "origin", "url", sourceGerritServerUrl
        .concat("/")
        .concat(projectName));
    config.setString("remote", "origin", "fetch", "+refs/*:refs/*");
    config.setString("http", null, "sslVerify", Boolean.FALSE.toString());
    config.save();
  }
}
