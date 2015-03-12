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

import static com.googlesource.gerrit.plugins.importer.ProgressMonitorUtil.updateAndEnd;

import com.google.inject.Singleton;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.util.Map;

@Singleton
class GitFetchStep {

  void fetch(String user, String password, Repository repo, ProgressMonitor pm)
      throws InvalidRemoteException, TransportException, GitAPIException,
      IOException {
    pm.beginTask("Fetch project", 1);
    CredentialsProvider cp =
        new UsernamePasswordCredentialsProvider(user, password);
    Git.wrap(repo).fetch()
        .setCredentialsProvider(cp)
        .setRemote("origin")
        .call();
    updateNonChangeRefs(repo);
    updateAndEnd(pm);
  }

  private void updateNonChangeRefs(Repository repo) throws IOException {
    Map<String, Ref> refs = repo.getRefDatabase().getRefs(
        ConfigureRepositoryStep.R_IMPORTS);
    for (Map.Entry<String, Ref> e : refs.entrySet()) {
      if (e.getKey().startsWith("changes/")) {
        continue;
      }
      String targetRef = Constants.R_REFS + e.getKey();
      RefUpdate ru = repo.updateRef(targetRef);
      ru.setNewObjectId(e.getValue().getObjectId());
      RefUpdate.Result result = ru.forceUpdate();
      switch (result) {
        case NEW:
        case FAST_FORWARD:
        case FORCED:
          break;
        default:
          throw new IOException(String.format(
              "Failed to update %s, RefUpdate.Result = %s", targetRef, result));
      }
    }
  }
}
