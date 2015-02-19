//Copyright (C) 2015 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Singleton;

import com.googlesource.gerrit.plugins.importer.ProjectRestEndpoint.Input;

import java.util.List;

@RequiresCapability(ImportCapability.ID)
@Singleton
class ProjectRestEndpoint implements RestModifyView<ConfigResource, Input> {
  public static class Input {
    public String from;
    public String user;
    public String pass;
    public List<String> projects;
  }

  @Override
  public String apply(ConfigResource rsrc, Input input) {
    return "TODO";
  }
}
