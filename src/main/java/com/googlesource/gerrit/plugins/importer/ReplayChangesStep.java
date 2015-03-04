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

import com.google.common.collect.Ordering;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.HashtagsUtil;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
  private final AccountUtil accountUtil;
  private final ReviewDb db;
  private final ChangeIndexer indexer;
  private final Provider<PostReview> postReview;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final HashtagsUtil hashtagsUtil;
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
      AccountUtil accountUtil,
      ReviewDb db,
      ChangeIndexer indexer,
      Provider<PostReview> postReview,
      ChangeUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil,
      HashtagsUtil hashtagsUtil,
      CurrentUser currentUser,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      @Assisted("from") String fromGerrit,
      @Assisted("user") String user,
      @Assisted("password") String password,
      @Assisted Repository repo,
      @Assisted Project.NameKey name) {
    this.replayRevisionsFactory = replayRevisionsFactory;
    this.accountUtil = accountUtil;
    this.db = db;
    this.indexer = indexer;
    this.postReview = postReview;
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
    this.hashtagsUtil = hashtagsUtil;
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

    replayInlineComments(change, c);
    replayMessages(change, c);
    addApprovals(change, c);
    addHashtags(change, c);

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

  private void replayInlineComments(Change change, ChangeInfo c) throws OrmException,
      RestApiException, IOException, NoSuchChangeException,
      NoSuchAccountException {
    for (PatchSet ps : db.patchSets().byChange(change.getId())) {
      Iterable<CommentInfo> comments = api.getComments(
          c._number, ps.getRevision().get());

      Table<Timestamp, Account.Id, List<CommentInfo>> t = TreeBasedTable.create(
          Ordering.natural(), new Comparator<Account.Id>() {
            @Override
            public int compare(Account.Id a1, Account.Id a2) {
              return a1.get() - a2.get();
            }}
          );

      for (CommentInfo comment : comments) {
        Account.Id id  = accountUtil.resolveUser(comment.author);
        List<CommentInfo> ci = t.get(comment.updated, id);
        if (ci == null) {
          ci = new ArrayList<>();
          t.put(comment.updated, id, ci);
        }
        ci.add(comment);
      }

      for (Timestamp ts : t.rowKeySet()) {
        for (Map.Entry<Account.Id, List<CommentInfo>> e : t.row(ts).entrySet()) {
          postComments(change, ps, e.getValue(), e.getKey(), ts);
        }
      }
    }
  }

  private void postComments(Change change, PatchSet ps,
      List<CommentInfo> comments, Account.Id author, Timestamp ts)
      throws RestApiException, OrmException, IOException, NoSuchChangeException {
    ReviewInput input = new ReviewInput();
    input.notify = NotifyHandling.NONE;
    input.comments = new HashMap<>();

    for (CommentInfo comment : comments) {
      if (!input.comments.containsKey(comment.path)) {
        input.comments.put(comment.path,
            new ArrayList<ReviewInput.CommentInput>());
      }

      ReviewInput.CommentInput commentInput = new ReviewInput.CommentInput();
      commentInput.id = comment.id;
      commentInput.inReplyTo = comment.inReplyTo;
      commentInput.line = comment.line;
      commentInput.message = comment.message;
      commentInput.path = comment.path;
      commentInput.range = comment.range;
      commentInput.side = comment.side;
      commentInput.updated = comment.updated;

      input.comments.get(comment.path).add(commentInput);
    }

    postReview.get().apply(
        new RevisionResource(new ChangeResource(control(change, author)), ps),
        input, ts);
  }

  private void replayMessages(Change change, ChangeInfo c)
      throws IOException, NoSuchChangeException, OrmException,
      NoSuchAccountException {
    for (ChangeMessageInfo msg : c.messages) {
      Account.Id userId = accountUtil.resolveUser(msg.author);
      Timestamp ts = msg.date;
      ChangeUpdate update = updateFactory.create(control(change, userId), ts);
      ChangeMessage cmsg =
          new ChangeMessage(new ChangeMessage.Key(change.getId(), msg.id),
              userId, ts, new PatchSet.Id(change.getId(), msg._revisionNumber));
      cmsg.setMessage(msg.message);
      cmUtil.addChangeMessage(db, update, cmsg);
      update.commit();
    }
  }

  private void addApprovals(Change change, ChangeInfo c)
      throws OrmException, NoSuchChangeException, IOException,
      NoSuchAccountException {
    List<PatchSetApproval> approvals = new ArrayList<>();
    for (Entry<String, LabelInfo> e : c.labels.entrySet()) {
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

  private void addHashtags(Change change, ChangeInfo c) throws AuthException,
      IOException, ValidationException, OrmException, NoSuchChangeException {
    HashtagsInput input = new HashtagsInput();
    input.add = new HashSet<>(c.hashtags);
    hashtagsUtil.setHashtags(control(change, c.owner), input, false, false);
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

  private ChangeControl control(Change change, AccountInfo acc)
      throws NoSuchChangeException {
    return control(change, new Account.Id(acc._accountId));
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
