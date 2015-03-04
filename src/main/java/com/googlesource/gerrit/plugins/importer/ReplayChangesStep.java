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
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

class ReplayChangesStep {

  interface Factory {
    ReplayChangesStep create(
        @Assisted("from") String fromGerrit,
        @Assisted("user") String user,
        @Assisted("password") String password,
        Repository repo,
        Project.NameKey name);
  }

  private final ReplayRevisionsStep.Factory replayRevisionsFactory;
  private final ReplayInlineCommentsStep.Factory replayInlineCommentsFactory;
  private final ReplayMessagesStep.Factory replayMessagesFactory;
  private final AddApprovalsStep.Factory addApprovalsFactory;
  private final AddHashtagsStep.Factory addHashtagsFactory;
  private final AccountUtil accountUtil;
  private final ReviewDb db;
  private final ChangeIndexer indexer;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final CurrentUser currentUser;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final String fromGerrit;
  private final RemoteApi api;
  private final Repository repo;
  private final Project.NameKey name;

  @Inject
  ReplayChangesStep(
      ReplayRevisionsStep.Factory replayRevisionsFactory,
      ReplayInlineCommentsStep.Factory replayInlineCommentsFactory,
      ReplayMessagesStep.Factory replayMessagesFactory,
      AddApprovalsStep.Factory addApprovalsFactory,
      AddHashtagsStep.Factory addHashtagsFactory,
      AccountUtil accountUtil,
      ReviewDb db,
      ChangeIndexer indexer,
      ChangeUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil,
      CurrentUser currentUser,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      @Assisted("from") String fromGerrit,
      @Assisted("user") String user,
      @Assisted("password") String password,
      @Assisted Repository repo,
      @Assisted Project.NameKey name) {
    this.replayRevisionsFactory = replayRevisionsFactory;
    this.replayInlineCommentsFactory = replayInlineCommentsFactory;
    this.replayMessagesFactory = replayMessagesFactory;
    this.addApprovalsFactory = addApprovalsFactory;
    this.addHashtagsFactory = addHashtagsFactory;
    this.accountUtil = accountUtil;
    this.db = db;
    this.indexer = indexer;
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
    this.currentUser = currentUser;
    this.genericUserFactory = genericUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.fromGerrit = fromGerrit;
    this.api = new RemoteApi(fromGerrit, user, password);
    this.repo = repo;
    this.name = name;
  }

  void replay() throws IOException, OrmException,
      NoSuchAccountException, NoSuchChangeException, RestApiException,
      ValidationException {
    List<ChangeInfo> changes = api.queryChanges(name.get());
    RevWalk rw = new RevWalk(repo);
    try {
      for (ChangeInfo c : changes) {
        replayChange(rw, c);
      }
    } finally {
      rw.release();
    }
  }

  private void replayChange(RevWalk rw, ChangeInfo c)
      throws IOException, OrmException, NoSuchAccountException,
      NoSuchChangeException, RestApiException, ValidationException {
    Change change = createChange(c);
    replayRevisionsFactory.create(repo, rw, change, c).replay();
    db.changes().insert(Collections.singleton(change));

    replayInlineCommentsFactory.create(change, c, api).replay();
    replayMessagesFactory.create(change, c).replay();
    addApprovalsFactory.create(change, c).add();
    addHashtagsFactory.create(change, c).add();

    insertLinkToOriginalChange(change, c);

    indexer.index(db, change);
  }

  private Change createChange(ChangeInfo c) throws OrmException,
      NoSuchAccountException {
    Change.Id changeId = new Change.Id(db.nextChangeId());

    Change change =
        new Change(new Change.Key(c.changeId), changeId, accountUtil.resolveUser(c.owner),
            new Branch.NameKey(new Project.NameKey(c.project),
            fullName(c.branch)), c.created);
    change.setStatus(Change.Status.forChangeStatus(c.status));
    change.setTopic(c.topic);
    return change;
  }

  private static String fullName(String branch) {
    if (branch.startsWith(Constants.R_HEADS)) {
      return branch;
    } else {
      return Constants.R_HEADS + branch;
    }
  }

  private void insertLinkToOriginalChange(Change change,
      ChangeInfo c) throws NoSuchChangeException, OrmException, IOException {
    insertMessage(change, "Imported from " + changeUrl(c));
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
