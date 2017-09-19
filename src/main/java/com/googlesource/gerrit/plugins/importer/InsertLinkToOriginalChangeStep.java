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
import com.google.gerrit.common.Nullable;
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
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

class InsertLinkToOriginalChangeStep {

  private final CurrentUser currentUser;
  private final ChangeUpdate.Factory updateFactory;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ReviewDb db;
  private final ChangeMessagesUtil cmUtil;
  private final String canonicalWebUrl;
  private final String fromGerrit;
  private final Change change;
  private final ChangeInfo changeInfo;
  private final boolean resume;

  interface Factory {
    InsertLinkToOriginalChangeStep create(
        @Nullable String fromGerrit,
        Change change,
        ChangeInfo changeInfo,
        boolean resume);
  }

  @Inject
  InsertLinkToOriginalChangeStep(CurrentUser currentUser,
      ChangeUpdate.Factory updateFactory,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeData.Factory changeDataFactory,
      ReviewDb db,
      ChangeMessagesUtil cmUtil,
      @CanonicalWebUrl String canonicalWebUrl,
      @Assisted @Nullable String fromGerrit,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo,
      @Assisted boolean resume) {
    this.currentUser = currentUser;
    this.updateFactory = updateFactory;
    this.genericUserFactory = genericUserFactory;
    this.changeDataFactory = changeDataFactory;
    this.db = db;
    this.cmUtil = cmUtil;
    this.canonicalWebUrl = canonicalWebUrl;
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
    url.append(ensureSlash(
        MoreObjects.firstNonNull(fromGerrit, canonicalWebUrl)));
    url.append(c._number);
    return url.toString();
  }

  private void insertMessage(Change change, String message)
      throws NoSuchChangeException, OrmException, IOException {
    Account.Id userId = ((IdentifiedUser) currentUser).getAccountId();
    ChangeData cd = changeDataFactory.create(db, change);
    ChangeUpdate update = updateFactory.create(cd.notes(), genericUserFactory.create(userId));
    ChangeMessage cmsg =
        new ChangeMessage(new ChangeMessage.Key(change.getId(),
            ChangeUtil.messageUuid()), userId, TimeUtil.nowTs(),
            change.currentPatchSetId());
    cmsg.setMessage(message);
    cmUtil.addChangeMessage(db, update, cmsg);
    update.commit();
  }

  private static String ensureSlash(String in) {
    if (in != null && !in.endsWith("/")) {
      return in + "/";
    }
    return in;
  }
}
