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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.change.HashtagsUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;

class AddHashtagsStep {

  interface Factory {
    AddHashtagsStep create(Change change, ChangeInfo changeInfo, boolean resume);
  }

  private static final Logger log = LoggerFactory
      .getLogger(AddHashtagsStep.class);

  private final HashtagsUtil hashtagsUtil;
  private final CurrentUser currentUser;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final String pluginName;
  private final Change change;
  private final ChangeInfo changeInfo;
  private final boolean resume;

  @Inject
  AddHashtagsStep(HashtagsUtil hashtagsUtil,
      CurrentUser currentUser,
      ChangeControl.GenericFactory changeControlFactory,
      @PluginName String pluginName,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo,
      @Assisted boolean resume) {
    this.hashtagsUtil = hashtagsUtil;
    this.currentUser = currentUser;
    this.changeControlFactory = changeControlFactory;
    this.pluginName = pluginName;
    this.change = change;
    this.changeInfo = changeInfo;
    this.resume = resume;
  }

  void add() throws IllegalArgumentException, AuthException, IOException,
      ValidationException, OrmException, NoSuchChangeException {
    ChangeControl ctrl = changeControlFactory.controlFor(change, currentUser);

    try {
      if (resume) {
        HashtagsInput input = new HashtagsInput();
        input.remove = ctrl.getNotes().load().getHashtags();
        hashtagsUtil.setHashtags(ctrl, input, false, false);
      }

      HashtagsInput input = new HashtagsInput();
      input.add = new HashSet<>(changeInfo.hashtags);
      hashtagsUtil.setHashtags(ctrl, input, false, false);
    } catch (AuthException e) {
      log.warn(String.format(
          "[%s] Hashtags cannot be set on change %s because the importing"
              + " user %s doesn't have permissions to edit hashtags"
              + " (e.g. assign the 'Edit Hashtags' global capability"
              + " and resume the import with the force option).",
          pluginName, ChangeTriplet.format(change), currentUser.getUserName()));
    }
  }
}
