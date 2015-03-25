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

package com.googlesource.gerrit.plugins.importer.client;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.plugin.client.Plugin;
import com.google.gerrit.plugin.client.rpc.RestApi;
import com.google.gerrit.plugin.client.screen.Screen;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.VerticalPanel;

import java.util.List;

public class ImportProjectListScreen extends VerticalPanel {
  static class Factory implements Screen.EntryPoint {
    @Override
    public void onLoad(Screen screen) {
      screen.setPageTitle("Imported Projects");
      screen.show(new ImportProjectListScreen());
    }
  }

  ImportProjectListScreen() {
    setStyleName("importer-imports-panel");

    new RestApi("config").id("server")
        .view(Plugin.get().getPluginName(), "projects")
        .get(new AsyncCallback<NativeMap<ImportProjectInfo>>() {
          @Override
          public void onSuccess(NativeMap<ImportProjectInfo> info) {
            display(info);
          }

          @Override
          public void onFailure(Throwable caught) {
            // never invoked
          }
        });
  }

  private void display(NativeMap<ImportProjectInfo> map) {
    int columns = 4;
    FlexTable t = new FlexTable();
    t.setStyleName("importer-importProjectTable");
    FlexCellFormatter fmt = t.getFlexCellFormatter();
    for (int c = 0; c < columns; c++) {
      fmt.addStyleName(0, c, "dataHeader");
      fmt.addStyleName(0, c, "topMostCell");
    }
    fmt.addStyleName(0, 0, "leftMostCell");

    t.setText(0, 0, "Project Name");
    t.setText(0, 1, "From");
    t.setText(0, 2, "Last Import By");
    t.setText(0, 3, "Last Import At");

    int row = 1;
    for (String project : map.keySet()) {
      ImportProjectInfo info = map.get(project);

      for (int c = 0; c < columns; c++) {
        fmt.addStyleName(row, c, "dataCell");
        fmt.addStyleName(row, 0, "leftMostCell");
      }

      t.setText(row, 0, project);

      String srcProjectUrl = ensureSlash(info.from())
          + "#/admin/projects/"
          + (info.name() != null ? info.name() : project);
      t.setWidget(row, 1, new Anchor(srcProjectUrl, srcProjectUrl));

      List<ImportInfo> importList = Natives.asList(info.imports());
      if (!importList.isEmpty()) {
        ImportInfo lastImportInfo = importList.get(importList.size() - 1);
        t.setText(row, 2, lastImportInfo.user().username());
        t.setText(row, 3, removeNs(lastImportInfo.timestamp()));
      } else {
        t.setText(row, 2, "N/A");
        t.setText(row, 3, "N/A");
      }

      row++;
    }

    add(t);
  }

  private static String removeNs(String timestamp) {
    return timestamp.substring(0, timestamp.lastIndexOf('.'));
  }

  private static String ensureSlash(String in) {
    if (in != null && !in.endsWith("/")) {
      return in + "/";
    }
    return in;
  }
}
