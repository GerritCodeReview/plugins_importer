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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ProjectsCollection implements
    ChildCollection<ConfigResource, ImportProjectResource>,
    AcceptsCreate<ConfigResource> {

  private final DynamicMap<RestView<ImportProjectResource>> views;
  private final ImportProject.Factory importProjectFactory;

  @Inject
  ProjectsCollection(
      DynamicMap<RestView<ImportProjectResource>> views,
      ImportProject.Factory importProjectFactory) {
    this.views = views;
    this.importProjectFactory = importProjectFactory;
  }

  @Override
  public RestView<ConfigResource> list() {
    throw new NotImplementedException();
  }

  @Override
  public ImportProjectResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException {
    throw new ResourceNotFoundException(id);
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
