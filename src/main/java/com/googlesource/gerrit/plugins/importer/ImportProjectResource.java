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

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.TypeLiteral;

import java.io.File;
import java.io.IOException;

class ImportProjectResource implements RestResource {
  static final TypeLiteral<RestView<ImportProjectResource>> IMPORT_PROJECT_KIND =
      new TypeLiteral<RestView<ImportProjectResource>>() {};

  private final Project.NameKey name;
  private final File file;

  ImportProjectResource(String name, File file) {
    this.name = new Project.NameKey(name);
    this.file = file;
  }

  public Project.NameKey getName() {
    return name;
  }

  public ImportProjectInfo getInfo() throws IOException {
    return ImportJson.parse(file);
  }
}
