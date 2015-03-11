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

import com.google.common.collect.Maps;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Map;

@Singleton
@RequiresCapability(ImportCapability.ID)
public class ListImportedProjects implements RestReadView<ConfigResource> {

  private final File lockRoot;

  @Inject
  ListImportedProjects(@PluginData File data) {
    this.lockRoot = data;
  }

  @Override
  public Map<String, ImportProject.Input> apply(ConfigResource rsrc)
      throws IOException {
    Map<String, ImportProject.Input> importedProjects = Maps.newTreeMap();
    for (File f : listImportFiles()) {
      importedProjects.put(f.getName(), parseParams(f));
    }
    return importedProjects;
  }

  private File[] listImportFiles() {
    return lockRoot.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return !name.endsWith(".lock");
      }
    });
  }

  private ImportProject.Input parseParams(File f) throws IOException {
    try (FileReader r = new FileReader(f)) {
      return OutputFormat.JSON_COMPACT.newGson().fromJson(r,
          new TypeToken<ImportProject.Input>() {}.getType());
    }
  }
}
