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

import static com.googlesource.gerrit.plugins.importer.client.TextBoxUtil.addPasswordTextBox;
import static com.googlesource.gerrit.plugins.importer.client.TextBoxUtil.addTextBox;
import static com.googlesource.gerrit.plugins.importer.client.TextBoxUtil.getValue;

import com.google.gerrit.plugin.client.Plugin;
import com.google.gerrit.plugin.client.rpc.RestApi;
import com.google.gerrit.plugin.client.screen.Screen;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

public class ImportGroupScreen extends VerticalPanel {
  static class Factory implements Screen.EntryPoint {
    @Override
    public void onLoad(Screen screen) {
      screen.setPageTitle("Import Group");
      screen.show(new ImportGroupScreen());
    }
  }

  private TextBox fromTxt;
  private TextBox nameTxt;
  private TextBox userTxt;
  private TextBox passTxt;
  private CheckBox importOwnerGroupCheckBox;
  private CheckBox importIncludedGroupsCheckBox;

  ImportGroupScreen() {
    setStyleName("importer-import-panel");

    fromTxt = addTextBox(this, "From*", "URL of the remote system from where the group should be imported");
    nameTxt = addTextBox(this, "Group Name*", "name of the group");
    userTxt = addTextBox(this, "Remote User*", "user on remote system");
    passTxt = addPasswordTextBox(this, "Password*", "password of remote user");
    importOwnerGroupCheckBox = addCheckBox("import owner group", "also import missing owner groups");
    importIncludedGroupsCheckBox = addCheckBox("import included groups", "also import missing included groups");

    HorizontalPanel buttons = new HorizontalPanel();
    add(buttons);

    Button importButton = new Button("Import");
    importButton.addStyleName("importer-importButton");
    importButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doImport();
      }
    });
    buttons.add(importButton);
    importButton.setEnabled(false);
    new OnEditEnabler(importButton, fromTxt);

    fromTxt.setFocus(true);
    importButton.setEnabled(false);
  }

  private CheckBox addCheckBox(String label, String infoMsg) {
    HorizontalPanel hp = new HorizontalPanel();
    CheckBox cb = new CheckBox(label);
    cb.setText(label);
    hp.add(cb);
    Image info = new Image(ImporterPlugin.RESOURCES.info());
    info.setTitle(infoMsg);
    hp.add(info);
    add(hp);
    return cb;
  }

  private void doImport() {
    ImportGroupInput in = ImportGroupInput.create();
    in.from(getValue(fromTxt));
    in.user(getValue(userTxt));
    in.pass(getValue(passTxt));
    in.importOwnerGroup(importOwnerGroupCheckBox.getValue());
    in.importIncludedGroups(importIncludedGroupsCheckBox.getValue());

    final String groupName = getValue(nameTxt);
    new RestApi("config").id("server").view(Plugin.get().getName(), "groups")
        .id(groupName).put(in, new AsyncCallback<JavaScriptObject>() {

      @Override
      public void onSuccess(JavaScriptObject result) {
        clearForm();
        Plugin.get().go("/admin/groups/" + groupName);

        final DialogBox successDialog = new DialogBox();
        successDialog.setText("Group Import");
        successDialog.setAnimationEnabled(true);

        Panel p = new VerticalPanel();
        p.setStyleName("importer-message-panel");
        p.add(new Label("The group was imported."));
        Button okButton = new Button("OK");
        okButton.addClickHandler(new ClickHandler() {
          public void onClick(ClickEvent event) {
            successDialog.hide();
          }
        });

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
    nameTxt.setValue("");
    userTxt.setValue("");
    passTxt.setValue("");
    importOwnerGroupCheckBox.setValue(false);
    importIncludedGroupsCheckBox.setValue(false);
  }
}
