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

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.File;

@Singleton
@RequiresCapability(ImportCapability.ID)
public class ProjectsCollection implements
    ChildCollection<ConfigResource, ImportProjectResource>,
    AcceptsCreate<ConfigResource> {

  private final DynamicMap<RestView<ImportProjectResource>> views;
  private final ImportProject.Factory importProjectFactory;
  private final Provider<ListImportedProjects> list;
  private final File lockRoot;

  @Inject
  ProjectsCollection(
      DynamicMap<RestView<ImportProjectResource>> views,
      ImportProject.Factory importProjectFactory,
      Provider<ListImportedProjects> list,
      @PluginData File data) {
    this.views = views;
    this.importProjectFactory = importProjectFactory;
    this.list = list;
    this.lockRoot = data;
  }

  @Override
  public RestView<ConfigResource> list() {
    return list.get();
  }

  @Override
  public ImportProjectResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException {
    File f = new File(lockRoot, id.get());
    if (!f.exists()) {
      throw new ResourceNotFoundException(id);
    }

    return new ImportProjectResource(id.get(), f);
  }

  @Override
  public DynamicMap<RestView<ImportProjectResource>> views() {
    return views;
  }

  @Override
  @SuppressWarnings("unchecked")
  public ImportProject create(ConfigResource parent, IdString id)
      throws RestApiException {
    return importProjectFactory.create(new Project.NameKey(id.get()));
  }
}
