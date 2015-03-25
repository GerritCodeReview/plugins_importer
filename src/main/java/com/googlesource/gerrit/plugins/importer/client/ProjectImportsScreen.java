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

import static com.googlesource.gerrit.plugins.importer.client.ImportProjectListScreen.projectUrl;
import static com.googlesource.gerrit.plugins.importer.client.ImportProjectListScreen.removeNs;

import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.plugin.client.Plugin;
import com.google.gerrit.plugin.client.rpc.RestApi;
import com.google.gerrit.plugin.client.screen.Screen;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.List;

public class ProjectImportsScreen extends VerticalPanel {
  static class Factory implements Screen.EntryPoint {
    @Override
    public void onLoad(Screen screen) {
      screen.setPageTitle("Imports of  " + screen.getToken(1));
      screen.show(new ProjectImportsScreen(screen.getToken(1)));
    }
  }

  ProjectImportsScreen(final String project) {
    setStyleName("importer-import-panel");

    new RestApi("config").id("server").view(Plugin.get().getPluginName(), "projects")
        .id(project).get(new AsyncCallback<ImportProjectInfo>() {
            @Override
            public void onSuccess(ImportProjectInfo importProjectInfo) {
              display(project, importProjectInfo);
            }

            @Override
            public void onFailure(Throwable caught) {
              // never invoked
            }
          });
  }

  private void display(final String project, ImportProjectInfo info) {
    MyTable t = new MyTable();
    t.setStyleName("importer-projectImportInfoTable");
    t.addRow("Project Name", project);
    String srcProjectUrl = projectUrl(info, project);
    t.addRow("From", new Anchor(srcProjectUrl, srcProjectUrl));
    t.addRow("Parent", info.parent());
    t.addRow("Actions", new ImportActionPanel(project));
    add(t);

    add(new Label("Imports:"));
    int columns = 3;
    FlexTable importsTable = new FlexTable();
    importsTable.setStyleName("importer-importProjectTable");
    FlexCellFormatter fmt = importsTable.getFlexCellFormatter();
    for (int c = 0; c < columns; c++) {
      fmt.addStyleName(0, c, "dataHeader");
      fmt.addStyleName(0, c, "topMostCell");
    }
    fmt.addStyleName(0, 0, "leftMostCell");

    importsTable.setText(0, 0, "Timestamp");
    importsTable.setText(0, 1, "User");
    importsTable.setText(0, 2, "Remote User");
    int row = 1;
    List<ImportInfo> imports = Natives.asList(info.imports());
    Collections.reverse(imports);
    for (ImportInfo importInfo : imports) {
      for (int c = 0; c < columns; c++) {
        fmt.addStyleName(row, c, "dataCell");
        fmt.addStyleName(row, 0, "leftMostCell");
      }

      importsTable.setText(row, 0, removeNs(importInfo.timestamp()));
      importsTable.setText(row, 1, importInfo.user().username());
      importsTable.setText(row, 2, importInfo.remoteUser());

      row++;
    }

    add(importsTable);
  }

  private static class MyTable extends FlexTable {
    private static int row = 0;

    private void addRow(String label, String value) {
      setWidget(row, 0, new Label(label + ":"));
      setWidget(row, 1, new Label(value));
      row++;
    }

    private void addRow(String label, Widget w) {
      setWidget(row, 0, new Label(label + ":"));
      setWidget(row, 1, w);
      row++;
    }
  }
}
