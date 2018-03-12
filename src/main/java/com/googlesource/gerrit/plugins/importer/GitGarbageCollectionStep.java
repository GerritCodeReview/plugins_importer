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

import java.util.List;

import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.git.GarbageCollection.Factory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class GitGarbageCollectionStep {

  private final Factory garbageCollectionFactory;

  @Inject
  GitGarbageCollectionStep(GarbageCollection.Factory garbageCollectionFactory) {
    this.garbageCollectionFactory = garbageCollectionFactory;
  }

  public GarbageCollectionResult run(List<Project.NameKey> projectNames) {
    return garbageCollectionFactory.create().run(projectNames);
  }
}
