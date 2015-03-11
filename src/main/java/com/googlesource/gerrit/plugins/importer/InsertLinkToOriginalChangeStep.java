//Copyright (C) 2015 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

class InsertLinkToOriginalChangeStep {

  private final CurrentUser currentUser;
  private final ChangeUpdate.Factory updateFactory;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ReviewDb db;
  private final ChangeMessagesUtil cmUtil;
  private final String fromGerrit;
  private final Change change;
  private final ChangeInfo changeInfo;
  private final boolean resume;

  interface Factory {
    InsertLinkToOriginalChangeStep create(String fromGerrit, Change change,
        ChangeInfo changeInfo, boolean resume);
  }

  @Inject
  InsertLinkToOriginalChangeStep(CurrentUser currentUser,
      ChangeUpdate.Factory updateFactory,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      ReviewDb db,
      ChangeMessagesUtil cmUtil,
      @Assisted String fromGerrit,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo,
      @Assisted boolean resume) {
    this.currentUser = currentUser;
    this.updateFactory = updateFactory;
    this.genericUserFactory = genericUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.cmUtil = cmUtil;
    this.fromGerrit = fromGerrit;
    this.change = change;
    this.changeInfo = changeInfo;
    this.resume = resume;
  }

  void insert() throws NoSuchChangeException, OrmException, IOException {
    insertMessage(change, (resume ? "Resumed import of " : "Imported from ")
        + changeUrl(changeInfo));
  }

  private String changeUrl(ChangeInfo c) {
    StringBuilder url = new StringBuilder();
    url.append(ensureSlash(fromGerrit)).append(c._number);
    return url.toString();
  }

  private void insertMessage(Change change, String message)
      throws NoSuchChangeException, OrmException, IOException {
    Account.Id userId = ((IdentifiedUser) currentUser).getAccountId();
    ChangeUpdate update = updateFactory.create(control(change, userId));
    ChangeMessage cmsg =
        new ChangeMessage(new ChangeMessage.Key(change.getId(),
            ChangeUtil.messageUUID(db)), userId, TimeUtil.nowTs(),
            change.currentPatchSetId());
    cmsg.setMessage(message);
    cmUtil.addChangeMessage(db, update, cmsg);
    update.commit();
  }

  private ChangeControl control(Change change, Account.Id id)
      throws NoSuchChangeException {
    return changeControlFactory.controlFor(change,
        genericUserFactory.create(id));
  }

  private static String ensureSlash(String in) {
    if (in != null && !in.endsWith("/")) {
      return in + "/";
    }
    return in;
  }
}
