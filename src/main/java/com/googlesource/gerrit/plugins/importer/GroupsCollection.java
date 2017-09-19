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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GroupsCollection implements
    ChildCollection<ConfigResource, ImportGroupResource>,
    AcceptsCreate<ConfigResource> {

  private final DynamicMap<RestView<ImportGroupResource>> views;
  private final ImportGroup.Factory importGroupFactory;

  @Inject
  GroupsCollection(
      DynamicMap<RestView<ImportGroupResource>> views,
      ImportGroup.Factory importGroupFactory) {
    this.views = views;
    this.importGroupFactory = importGroupFactory;
  }

  @Override
  public RestView<ConfigResource> list() {
    throw new NotImplementedException();
  }

  @Override
  public ImportGroupResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException {
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<ImportGroupResource>> views() {
    return views;
  }

  @Override
  public ImportGroup create(ConfigResource parent, IdString id)
      throws RestApiException {
    return importGroupFactory.create(new AccountGroup.NameKey(id.get()));
  }
}
