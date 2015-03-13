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

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

class AddApprovalsStep {

  interface Factory {
    AddApprovalsStep create(Change change, ChangeInfo changeInfo);
  }

  private final AccountUtil accountUtil;
  private final ChangeUpdate.Factory updateFactory;
  private final ReviewDb db;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Change change;
  private final ChangeInfo changeInfo;

  @Inject
  public AddApprovalsStep(AccountUtil accountUtil,
      ChangeUpdate.Factory updateFactory,
      ReviewDb db,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo) {
    this.accountUtil = accountUtil;
    this.updateFactory = updateFactory;
    this.db = db;
    this.genericUserFactory = genericUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.change = change;
    this.changeInfo = changeInfo;
  }

  void add() throws OrmException, NoSuchChangeException, IOException,
      NoSuchAccountException {
    List<PatchSetApproval> approvals = new ArrayList<>();
    for (Entry<String, LabelInfo> e : changeInfo.labels.entrySet()) {
      String labelName = e.getKey();
      LabelInfo label = e.getValue();
      if (label.all != null) {
        for (ApprovalInfo a : label.all) {
          Account.Id user = accountUtil.resolveUser(a);
          ChangeControl ctrl = control(change, a);
          LabelType labelType = ctrl.getLabelTypes().byLabel(labelName);
          approvals.add(new PatchSetApproval(
              new PatchSetApproval.Key(change.currentPatchSetId(), user,
                  labelType.getLabelId()), a.value.shortValue(), a.date));
          ChangeUpdate update = updateFactory.create(ctrl);
          update.putApproval(labelName, a.value.shortValue());
          update.commit();
        }
      }
    }
    db.patchSetApprovals().upsert(approvals);
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
