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

import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;

class ReplayMessagesStep {

  interface Factory {
    ReplayMessagesStep create(Change change, ChangeInfo changeInfo,
        boolean resume);
  }

  private final AccountUtil accountUtil;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final ReviewDb db;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Change change;
  private final ChangeInfo changeInfo;
  private final boolean resume;

  @Inject
  public ReplayMessagesStep(AccountUtil accountUtil,
      ChangeUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      ReviewDb db,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo,
      @Assisted boolean resume) {
    this.accountUtil = accountUtil;
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
    this.db = db;
    this.genericUserFactory = genericUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.change = change;
    this.changeInfo = changeInfo;
    this.resume = resume;
  }

  void replay(GerritApi api) throws NoSuchAccountException, NoSuchChangeException,
      OrmException, IOException, BadRequestException {
    for (ChangeMessageInfo msg : changeInfo.messages) {
      ChangeMessage.Key msgKey = new ChangeMessage.Key(change.getId(), msg.id);
      if (resume && db.changeMessages().get(msgKey) != null) {
        // already replayed
        continue;
      }

      Timestamp ts = msg.date;
      if (msg.author != null) {
        Account.Id userId = accountUtil.resolveUser(api, msg.author);
        ChangeUpdate update = updateFactory.create(control(change, userId), ts);
        ChangeMessage cmsg =
            new ChangeMessage(msgKey, userId, ts, new PatchSet.Id(
                change.getId(), msg._revisionNumber));
        cmsg.setMessage(msg.message);
        cmUtil.addChangeMessage(db, update, cmsg);
        update.commit();
      } else {
        // Message create by the GerritPersonIdent user
        ChangeMessage cmsg =
            new ChangeMessage(new ChangeMessage.Key(change.getId(), msg.id),
                null, ts, new PatchSet.Id(change.getId(), msg._revisionNumber));
        cmsg.setMessage(msg.message);
        db.changeMessages().insert(Collections.singleton(cmsg));
      }
    }
  }

  private ChangeControl control(Change change, Account.Id id)
      throws NoSuchChangeException {
    return changeControlFactory.controlFor(change,
        genericUserFactory.create(id));
  }
}
