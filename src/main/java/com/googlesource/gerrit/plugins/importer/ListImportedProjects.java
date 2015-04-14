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

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

@Singleton
@RequiresCapability(ImportCapability.ID)
public class ListImportedProjects implements RestReadView<ConfigResource> {

  private final ProjectsCollection projects;

  @Option(name = "--match", metaVar = "MATCH",
      usage = "List only projects containing this substring, case insensitive")
  public void setMatch(String match) {
    this.match = match.toLowerCase(Locale.ENGLISH);
  }

  private String match;

  @Inject
  ListImportedProjects(ProjectsCollection projects) {
    this.projects = projects;
  }

  @Override
  public Map<String, ImportProjectInfo> apply(ConfigResource rsrc)
      throws IOException {
    Map<String, ImportProjectInfo> importedProjects = Maps.newTreeMap();
    for (File f : listImportFiles()) {
      importedProjects.put(projects.FS_LAYOUT.resolveProjectName(f), ImportJson.parse(f));
    }
    return importedProjects;
  }

  private Collection<File> listImportFiles() {
    Collection<File> importFiles = new HashSet<>();
    for (File f : Files.fileTreeTraverser().preOrderTraversal(projects.FS_LAYOUT.getLockRoot())) {
      if (f.isFile()
          && !f.getName().endsWith(".lock")
          && (match == null || f.getName().toLowerCase(Locale.ENGLISH).contains(match))) {
        importFiles.add(f);
      }
    }
    return importFiles;
  }
}
