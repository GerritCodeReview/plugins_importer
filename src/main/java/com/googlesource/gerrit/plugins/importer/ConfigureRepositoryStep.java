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

import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

import java.io.IOException;

@Singleton
class ConfigureRepositoryStep {

  static final String R_IMPORTS = "refs/imports/";

  void configure(Repository repo, Project.NameKey name, String originUrl, ProgressMonitor pm)
      throws IOException {
    pm.beginTask("Configure repository", 1);
    StoredConfig config = repo.getConfig();
    config.setString("remote", "origin", "url", originUrl
        .concat("/")
        .concat(name.get()));
    config.setString("remote", "origin", "fetch", "+refs/*:" + R_IMPORTS + "*");
    config.setString("http", null, "sslVerify", Boolean.FALSE.toString());
    config.save();
    updateAndEnd(pm);
  }
}
