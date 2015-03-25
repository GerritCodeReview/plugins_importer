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
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
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
  private final TextBox userTxt;
  private final TextBox passTxt;

  public ResumeImportDialog(final String project) {
    super(/* auto hide */false, /* modal */true);
    setGlassEnabled(true);
    setText("Resume Project Import");

    FlowPanel buttons = new FlowPanel();

    resumeButton = new Button();
    resumeButton.setText("Resume");
    resumeButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();

        ResumeImportProjectInput in = ResumeImportProjectInput.create();
        in.user(getValue(userTxt));
        in.pass(getValue(passTxt));

        new RestApi("config").id("server")
            .view(Plugin.get().getName(), "projects").id(project)
            .view("resume").put(in, new AsyncCallback<JavaScriptObject>() {
              @Override
              public void onSuccess(JavaScriptObject result) {
                Plugin.get().go("/admin/projects/" + project);

                final DialogBox successDialog = new DialogBox();
                successDialog.setText("Resume Project Import");
                successDialog.setAnimationEnabled(true);

                Panel p = new VerticalPanel();
                p.setStyleName("importer-message-panel");
                p.add(new Label("The project import was resumed."));
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
    });
    buttons.add(resumeButton);

    cancelButton = new Button();
    cancelButton.addStyleName("importer-cancel-button");
    cancelButton.setText("Cancel");
    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
      }
    });
    buttons.add(cancelButton);

    FlowPanel center = new FlowPanel();
    Label msg = new Label("Resume import of project '" + project + "'");
    msg.addStyleName("importer-resume-message");
    center.add(msg);

    userTxt = addTextBox(center, "Remote User*", "user on remote system");
    passTxt = addPasswordTextBox(center, "Password*", "password of remote user");

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
