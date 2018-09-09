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

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.change.SetHashtagsOp;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AddHashtagsStep {

  interface Factory {
    AddHashtagsStep create(Change change, ChangeInfo changeInfo, boolean resume);
  }

  private static final Logger log = LoggerFactory.getLogger(AddHashtagsStep.class);

  private final CurrentUser currentUser;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Change change;
  private final ChangeInfo changeInfo;
  private final boolean resume;
  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final SetHashtagsOp.Factory hashtagsFactory;

  @Inject
  AddHashtagsStep(
      CurrentUser currentUser,
      ChangeControl.GenericFactory changeControlFactory,
      Provider<ReviewDb> db,
      BatchUpdate.Factory batchUpdateFactory,
      SetHashtagsOp.Factory hashtagsFactory,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo,
      @Assisted boolean resume) {
    this.currentUser = currentUser;
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.batchUpdateFactory = batchUpdateFactory;
    this.hashtagsFactory = hashtagsFactory;
    this.change = change;
    this.changeInfo = changeInfo;
    this.resume = resume;
  }

  void add()
      throws IllegalArgumentException, OrmException, NoSuchChangeException, UpdateException,
          RestApiException {
    ChangeControl ctrl = changeControlFactory.controlFor(db.get(), change, currentUser);

    try {
      if (resume) {
        HashtagsInput input = new HashtagsInput();
        input.remove = ctrl.getNotes().load().getHashtags();
        try (BatchUpdate bu =
            batchUpdateFactory.create(
                db.get(), change.getProject(), currentUser, TimeUtil.nowTs())) {
          SetHashtagsOp op = hashtagsFactory.create(input);
          bu.addOp(change.getId(), op);
          bu.execute();
        }
      }

      HashtagsInput input = new HashtagsInput();
      input.add = new HashSet<>(changeInfo.hashtags);
      try (BatchUpdate bu =
          batchUpdateFactory.create(db.get(), change.getProject(), currentUser, TimeUtil.nowTs())) {
        SetHashtagsOp op = hashtagsFactory.create(input);
        bu.addOp(change.getId(), op);
        bu.execute();
      }
    } catch (AuthException e) {
      log.warn(
          String.format(
              "Hashtags cannot be set on change %s because the importing"
                  + " user %s doesn't have permissions to edit hashtags"
                  + " (e.g. assign the 'Edit Hashtags' global capability"
                  + " and resume the import with the force option).",
              ChangeTriplet.format(change), currentUser.getUserName()));
    }
  }
}
