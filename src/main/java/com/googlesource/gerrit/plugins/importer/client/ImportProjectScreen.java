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

import static com.googlesource.gerrit.plugins.importer.client.InputUtil.addPasswordTextBox;
import static com.googlesource.gerrit.plugins.importer.client.InputUtil.addTextBox;
import static com.googlesource.gerrit.plugins.importer.client.InputUtil.getValue;

import com.google.gerrit.plugin.client.Plugin;
import com.google.gerrit.plugin.client.rpc.RestApi;
import com.google.gerrit.plugin.client.screen.Screen;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

public class ImportProjectScreen extends VerticalPanel {
  static class Factory implements Screen.EntryPoint {
    @Override
    public void onLoad(Screen screen) {
      screen.setPageTitle("Import Project");
      screen.show(new ImportProjectScreen());
    }
  }

  private TextBox fromTxt;
  private TextBox srcNameTxt;
  private TextBox targetNameTxt;
  private TextBox userTxt;
  private TextBox passTxt;
  private TextBox parentTxt;

  ImportProjectScreen() {
    setStyleName("importer-import-panel");

    fromTxt = addTextBox(this, "From*", "URL of the remote system from where the project should be imported");
    srcNameTxt = addTextBox(this, "Project Name in Source*", "name of project in source system");
    targetNameTxt = addTextBox(this, "Target Project Name", "name of project in target system"
        + " (if not specified it is assumed to be the same name as in the source system)");
    userTxt = addTextBox(this, "Remote User*", "user on remote system");
    passTxt = addPasswordTextBox(this, "Password*", "password of remote user");
    parentTxt = addTextBox(this, "Parent", "name of parent project in target system"
        + "(if not specified it is assumed to be the same parent as in the source system)");

    HorizontalPanel buttons = new HorizontalPanel();
    add(buttons);

    Button importButton = new Button("Import");
    importButton.addStyleName("importer-importButton");
    importButton.addClickHandler(event -> doImport());
    buttons.add(importButton);
    importButton.setEnabled(false);
    new OnEditEnabler(importButton, fromTxt);

    fromTxt.setFocus(true);
    importButton.setEnabled(false);
  }

  private void doImport() {
    final String targetName = getValue(targetNameTxt).length() == 0
        ? getValue(srcNameTxt) : getValue(targetNameTxt);

    ImportProjectInput in = ImportProjectInput.create();
    in.from(getValue(fromTxt));
    in.name(getValue(srcNameTxt));
    in.user(getValue(userTxt));
    in.pass(getValue(passTxt));
    in.parent(getValue(parentTxt));

    new RestApi("config").id("server").view(Plugin.get().getName(), "projects")
        .id(targetName).put(in, new AsyncCallback<ImportStatisticInfo>() {

      @Override
      public void onSuccess(ImportStatisticInfo result) {
        clearForm();
        Plugin.get().go("/admin/projects/" + targetName);

        final DialogBox successDialog = new DialogBox();
        successDialog.setText("Project Import");
        successDialog.setAnimationEnabled(true);

        Panel p = new VerticalPanel();
        p.setStyleName("importer-message-panel");
        p.add(new Label("The project was imported."));
        p.add(new Label("Created Changes: " + result.numChangesCreated()));
        Button okButton = new Button("OK");
        okButton.addClickHandler(event -> successDialog.hide());

        p.add(okButton);
        successDialog.add(p);

        successDialog.center();
        successDialog.show();
      }

      @Override
      public void onFailure(Throwable caught) {
      }
    });
  }

  private void clearForm() {
    fromTxt.setValue("");
    srcNameTxt.setValue("");
    targetNameTxt.setValue("");
    userTxt.setValue("");
    passTxt.setValue("");
    parentTxt.setValue("");
  }
}
