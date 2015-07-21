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

import com.google.gerrit.plugin.client.Plugin;
import com.google.gerrit.plugin.client.rpc.NoContent;
import com.google.gerrit.plugin.client.rpc.RestApi;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

public class CompleteImportDialog extends AutoCenterDialogBox {

  private final Button cancelButton;
  private final Button completeButton;

  public CompleteImportDialog(final String project, final boolean copy) {
    super(/* auto hide */false, /* modal */true);
    setGlassEnabled(true);
    setText("Complete Project " + (copy ? "Copy" : "Import"));

    FlowPanel buttons = new FlowPanel();

    completeButton = new Button();
    completeButton.setText("Complete");
    completeButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();

        new RestApi("config").id("server")
            .view(Plugin.get().getName(), "projects").id(project)
            .delete(new AsyncCallback<NoContent>() {
              @Override
              public void onSuccess(NoContent result) {
                Plugin.get().go("/x/" + Plugin.get().getName() + "/list");

                final DialogBox successDialog = new DialogBox();
                successDialog.setText("Project "
                    + (copy ? "Copy" : "Import") + " Completed");
                successDialog.setAnimationEnabled(true);

                Panel p = new VerticalPanel();
                p.setStyleName("importer-message-panel");
                p.add(new Label("The project "
                  + (copy ? "copy" : "import") + " was completed."));
                Button okButton = new Button("OK");
                okButton.addClickHandler(new ClickHandler() {
                  @Override
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
    buttons.add(completeButton);

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
    Label msg = new Label("Complete " + (copy ? "copy" : "import")
        + " of project '" + project + "'");
    msg.addStyleName("importer-complete-message");
    center.add(msg);

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
