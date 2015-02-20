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

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.GitRepositoryManager;
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
import java.util.List;

@RequiresCapability(ImportCapability.ID)
@Singleton
class ProjectRestEndpoint implements RestModifyView<ConfigResource, Input> {
  public static class Input {
    public String from;
    public String user;
    public String pass;
    public List<String> projects;
  }

  private static final String IMPORT = "IMPORT";

  private final GitRepositoryManager git;

  @Inject
  ProjectRestEndpoint(GitRepositoryManager git) {
    this.git = git;
  }

  @Override
  public String apply(ConfigResource rsrc, Input input) {

    StringBuilder result = new StringBuilder();
    CredentialsProvider cp =
        new UsernamePasswordCredentialsProvider(input.user, input.pass);

    for(String projectName : input.projects) {
      Project.NameKey name = new Project.NameKey(projectName);
      try {
        git.openRepository(name);
        result.append(format("Repository %s already exists", projectName));
        continue;
      } catch (RepositoryNotFoundException e) {
        // Ideal, project doesn't exist
      } catch (IOException e) {
        result.append(e.getMessage());
        continue;
      }

      Repository repo;
      try {
        repo = git.createRepository(name);
      } catch (IOException e) {
        result.append(format("Error: %s, skipping project %s", e, projectName));
        continue;
      }

      try {
        LockFile importing = getLockFile(repo);
        if (!importing.lock()) {
          result.append(format("Project %s is being imported from another session"
              + ", skipping", projectName));
          continue;
        }

        try {
          setupProjectConfiguration(input.from, projectName, repo.getConfig());
          FetchResult fetchResult = Git.wrap(repo).fetch()
              .setCredentialsProvider(cp)
              .setRemote("origin")
              .call();
          result.append(format("[INFO] Project '%s' imported: %s",
              projectName, fetchResult.getMessages()));
        } finally {
          importing.commit();
        }
      } catch(IOException | GitAPIException e) {
        result.append(format("[ERROR] Unable to transfere project '%s' from"
            + " source gerrit host '%s': %s",
            projectName, input.from, e.getMessage()));
      } finally {
        repo.close();
      }
    }
    return result.toString();
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

  private LockFile getLockFile(Repository repo) {
   File importStatus = new File(repo.getDirectory(), IMPORT);
   return new LockFile(importStatus, FS.DETECTED);
  }
}
