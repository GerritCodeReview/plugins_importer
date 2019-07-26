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

import com.google.common.base.MoreObjects;

import java.util.List;

public class ImportProjectInfo {
  public String from;
  public String name;
  public String parent;
  public List<ImportInfo> imports;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("from", from)
        .add("name", name)
        .add("parent", parent)
        .add("imports", stringify(imports))
        .toString();
  }

  private String stringify(List<ImportInfo> imports) {
    return '[' + imports.stream()
        .map(ImportInfo::toString)
        .reduce("", (s1, s2) -> s1 + ", " + s2)
        + ']';
  }

}
