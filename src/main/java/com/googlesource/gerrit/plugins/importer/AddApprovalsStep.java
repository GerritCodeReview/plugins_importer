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
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

class AddApprovalsStep {

  interface Factory {
    AddApprovalsStep create(Change change, ChangeInfo changeInfo, boolean resume);
  }

  private static final Logger log = LoggerFactory
      .getLogger(ReplayInlineCommentsStep.class);

  private final AccountUtil accountUtil;
  private final ChangeUpdate.Factory updateFactory;
  private final ReviewDb db;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Change change;
  private final ChangeInfo changeInfo;
  private final boolean resume;
  private final String pluginName;

  @Inject
  public AddApprovalsStep(AccountUtil accountUtil,
      ChangeUpdate.Factory updateFactory,
      ReviewDb db,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      @PluginName String pluginName,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo,
      @Assisted boolean resume) {
    this.accountUtil = accountUtil;
    this.updateFactory = updateFactory;
    this.db = db;
    this.genericUserFactory = genericUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.change = change;
    this.changeInfo = changeInfo;
    this.resume = resume;
    this.pluginName = pluginName;
  }

  void add(GerritApi api) throws OrmException, NoSuchChangeException, IOException,
      NoSuchAccountException, BadRequestException {
    if (resume) {
      db.patchSetApprovals().delete(
          db.patchSetApprovals().byChange(change.getId()));
    }

    List<PatchSetApproval> approvals = new ArrayList<>();
    for (Entry<String, LabelInfo> e : changeInfo.labels.entrySet()) {
      String labelName = e.getKey();
      LabelInfo label = e.getValue();
      if (label.all != null) {
        for (ApprovalInfo a : label.all) {
          Account.Id user = accountUtil.resolveUser(api, a);
          ChangeControl ctrl = control(change, a);
          LabelType labelType = ctrl.getLabelTypes().byLabel(labelName);
          if(labelType == null) {
            log.warn(String.format("[%s] Label '%s' not found in target system."
                + " This label was referenced by an approval provided from '%s'"
                + " for change '%s'."
                + " This approval will be skipped. In order to import this"
                + " approval configure the missing label and resume the import."
                , pluginName, labelName, a.username, changeInfo.id));
            continue;
          }
          approvals.add(new PatchSetApproval(
              new PatchSetApproval.Key(change.currentPatchSetId(), user,
                  labelType.getLabelId()), a.value.shortValue(),
                  MoreObjects.firstNonNull(a.date, TimeUtil.nowTs())));
          ChangeUpdate update = updateFactory.create(ctrl);
          if (a.value != 0) {
            update.putApproval(labelName, a.value.shortValue());
          } else {
            update.removeApproval(labelName);
          }
          update.commit();
        }
      }
    }
    db.patchSetApprovals().insert(approvals);
  }

  private ChangeControl control(Change change, AccountInfo acc)
      throws NoSuchChangeException {
    return control(change, new Account.Id(acc._accountId));
  }

  private ChangeControl control(Change change, Account.Id id)
      throws NoSuchChangeException {
    return changeControlFactory.controlFor(change,
        genericUserFactory.create(id));
  }
}
