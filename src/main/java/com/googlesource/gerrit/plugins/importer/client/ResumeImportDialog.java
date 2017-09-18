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

import static com.googlesource.gerrit.plugins.importer.client.InputUtil.addCheckBox;
import static com.googlesource.gerrit.plugins.importer.client.InputUtil.addPasswordTextBox;
import static com.googlesource.gerrit.plugins.importer.client.InputUtil.addTextBox;
import static com.googlesource.gerrit.plugins.importer.client.InputUtil.getValue;

import com.google.gerrit.plugin.client.Plugin;
import com.google.gerrit.plugin.client.rpc.RestApi;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

public class ResumeImportDialog extends AutoCenterDialogBox {

  private final Button cancelButton;
  private final Button resumeButton;
  private TextBox userTxt;
  private TextBox passTxt;
  private CheckBox forceCheckBox;

  public ResumeImportDialog(final String project, final boolean copy) {
    super(/* auto hide */false, /* modal */true);
    setGlassEnabled(true);
    setText("Resume Project " + (copy ? "Copy" : "Import"));

    FlowPanel buttons = new FlowPanel();

    resumeButton = new Button();
    resumeButton.setText("Resume");
    resumeButton.addClickHandler(event -> {
        hide();

        RestApi restApi;
        ResumeImportProjectInput in = ResumeImportProjectInput.create();
        in.force(forceCheckBox.getValue());
        if (copy) {
          restApi = new RestApi("projects").id(project)
              .view(Plugin.get().getName(), "copy.resume");
        } else {
          restApi = new RestApi("config").id("server")
              .view(Plugin.get().getName(), "projects").id(project)
              .view("resume");
          in.user(getValue(userTxt));
          in.pass(getValue(passTxt));
        }

        restApi.put(in, new AsyncCallback<ResumeImportStatisticInfo>() {
              @Override
              public void onSuccess(ResumeImportStatisticInfo result) {
                Plugin.get().go("/x/" + Plugin.get().getName() + "/list");

                final DialogBox successDialog = new DialogBox();
                successDialog.setText("Resume " + (copy ? "Copy" : "Import") + " Import");
                successDialog.setAnimationEnabled(true);

                Panel p = new VerticalPanel();
                p.setStyleName("importer-message-panel");
                p.add(new Label("The project " + (copy ? "copy" : "import") + " was resumed."));
                p.add(new Label("Created Changes: " + result.numChangesCreated()));
                p.add(new Label("Updated Changes: " + result.numChangesUpdated()));
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
    });
    buttons.add(resumeButton);

    cancelButton = new Button();
    cancelButton.addStyleName("importer-cancel-button");
    cancelButton.setText("Cancel");
    cancelButton.addClickHandler(event -> hide());
    buttons.add(cancelButton);

    FlowPanel center = new FlowPanel();
    Label msg = new Label("Resume " + (copy ? "copy" : "import")
        + " of project '" + project + "'");
    msg.addStyleName("importer-resume-message");
    center.add(msg);

    if (!copy) {
      userTxt = addTextBox(center, "Remote User*", "user on remote system");
      passTxt = addPasswordTextBox(center, "Password*", "password of remote user");
    }
    forceCheckBox = addCheckBox(center, "Force", "whether resume should be done forcefully");

    center.add(buttons);
    add(center);

    setWidget(center);
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    cancelButton.setFocus(true);
  }
}
