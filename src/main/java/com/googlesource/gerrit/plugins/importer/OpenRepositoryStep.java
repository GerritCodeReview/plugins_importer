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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

@Singleton
class OpenRepositoryStep {

  private final GitRepositoryManager git;

  @Inject
  OpenRepositoryStep(GitRepositoryManager git) {
    this.git = git;
  }

  Repository open(Project.NameKey name, StringBuffer out) {
    try {
      git.openRepository(name);
      out.append(format("Repository %s already exists.", name.get()));
      return null;
    } catch (RepositoryNotFoundException e) {
      // Project doesn't exist
    } catch (IOException e) {
      out.append(e.getMessage());
      return null;
    }

    try {
      return git.createRepository(name);
    } catch(IOException e) {
      out.append(format("Error: %s, skipping project %s", e, name.get()));
      return null;
    }
  }
}
