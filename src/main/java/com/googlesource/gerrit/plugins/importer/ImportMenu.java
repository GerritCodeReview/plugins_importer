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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;

public class ImportMenu implements TopMenu {
  private final String pluginName;
  private final Provider<CurrentUser> userProvider;
  private final List<MenuEntry> menuEntries;

  @Inject
  ImportMenu(
      @PluginName String pluginName,
      Provider<CurrentUser> userProvider) {
    this.pluginName = pluginName;
    this.userProvider = userProvider;
    menuEntries = Lists.newArrayList();

    List<MenuItem> projectItems = Lists.newArrayListWithExpectedSize(2);
    List<MenuItem> peopleItems = Lists.newArrayListWithExpectedSize(1);
    if (canImport()) {
      projectItems.add(new MenuItem("Import Project", "#/x/" + pluginName + "/project", ""));
      projectItems.add(new MenuItem("List Imports", "#/x/" + pluginName + "/list", ""));
      peopleItems.add(new MenuItem("Import Group", "#/x/" + pluginName + "/group", ""));
    }
    if (!projectItems.isEmpty()) {
      menuEntries.add(new MenuEntry("Projects", projectItems));
    }
    if (!peopleItems.isEmpty()) {
      menuEntries.add(new MenuEntry("People", peopleItems));
    }
  }

  private boolean canImport() {
    CapabilityControl ctl = userProvider.get().getCapabilities();
    return ctl.canAdministrateServer()
        || ctl.canPerform(pluginName + "-" + ImportCapability.ID);
  }

  @Override
  public List<MenuEntry> getEntries() {
    return menuEntries;
  }
}
