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

import com.google.inject.Singleton;

import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.util.Map;

@Singleton
class GitFetchStep {

  void fetch(String user, String password, Repository repo, ProgressMonitor pm)
      throws InvalidRemoteException, TransportException, GitAPIException,
      IOException {
    pm.beginTask("Fetch project", 1);
    FetchCommand fetch = Git.wrap(repo).fetch();
    if (user != null) {
      fetch.setCredentialsProvider(
          new UsernamePasswordCredentialsProvider(user, password));
    }
    fetch.setRemote("origin").call();
    updateNonChangeRefs(repo);
    updateAndEnd(pm);
  }

  private void updateNonChangeRefs(Repository repo) throws IOException {
    Map<String, Ref> refs = repo.getRefDatabase().getRefs(
        ConfigureRepositoryStep.R_IMPORTS);
    for (Map.Entry<String, Ref> e : refs.entrySet()) {
      String name = e.getKey();
      if (name.startsWith("imports/")) {
        continue;
      }
      if (name.startsWith("cache-automerge/")) {
        continue;
      }
      if (name.startsWith("changes/")) {
        continue;
      }
      if (name.startsWith("users/") && name.contains("/edit")) {
        continue;
      }
      String targetRef = Constants.R_REFS + name;
      RefUpdate ru = repo.updateRef(targetRef);
      ru.setNewObjectId(e.getValue().getObjectId());
      RefUpdate.Result result = ru.forceUpdate();
      switch (result) {
        case NEW:
        case FAST_FORWARD:
        case FORCED:
        case NO_CHANGE:
          break;
        case IO_FAILURE:
        case LOCK_FAILURE:
        case NOT_ATTEMPTED:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case RENAMED:
        case REJECTED_MISSING_OBJECT:
        case REJECTED_OTHER_REASON:
        default:
          throw new IOException(String.format(
              "Failed to update %s, RefUpdate.Result = %s", targetRef, result));
      }
    }
  }
}
