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

import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.HashtagsUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.HashSet;

class AddHashtagsStep {

  interface Factory {
    AddHashtagsStep create(Change change, ChangeInfo changeInfo, boolean resume);
  }

  private final HashtagsUtil hashtagsUtil;
  private final CurrentUser currentUser;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Change change;
  private final ChangeInfo changeInfo;
  private final boolean resume;

  @Inject
  AddHashtagsStep(HashtagsUtil hashtagsUtil,
      CurrentUser currentUser,
      ChangeControl.GenericFactory changeControlFactory,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo,
      @Assisted boolean resume) {
    this.hashtagsUtil = hashtagsUtil;
    this.change = change;
    this.changeInfo = changeInfo;
    this.currentUser = currentUser;
    this.changeControlFactory = changeControlFactory;
    this.resume = resume;
  }

  void add() throws IllegalArgumentException, AuthException, IOException,
      ValidationException, OrmException, NoSuchChangeException {
    ChangeControl ctrl = changeControlFactory.controlFor(change, currentUser);

    if (resume) {
      HashtagsInput input = new HashtagsInput();
      input.remove = ctrl.getNotes().load().getHashtags();
      hashtagsUtil.setHashtags(ctrl, input, false, false);
    }

    HashtagsInput input = new HashtagsInput();
    input.add = new HashSet<>(changeInfo.hashtags);
    hashtagsUtil.setHashtags(ctrl, input, false, false);
  }
}
