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
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.ProgressMonitor;

@Singleton
class ConfigureProjectStep {
  private final ProjectCache projectCache;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllProjectsName allProjectsName;

  @Inject
  ConfigureProjectStep(
      ProjectCache projectCache,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllProjectsName allProjectsName) {
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allProjectsName = allProjectsName;
  }

  void configure(Project.NameKey name, Project.NameKey parentName, ProgressMonitor pm)
      throws IOException {
    pm.beginTask("Configure project", 1);
    ProjectConfig projectConfig = projectCache.get(name).getConfig();
    Project p = projectConfig.getProject();
    if (!p.getParent(allProjectsName).equals(parentName)) {
      p.setParentName(parentName);
      MetaDataUpdate md = metaDataUpdateFactory.get().create(name);
      md.setMessage("Set Parent\n");
      projectConfig.commit(md);
      projectCache.evict(p);
    }
    updateAndEnd(pm);
  }
}
