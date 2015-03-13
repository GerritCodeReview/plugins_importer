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

import com.google.common.collect.Ordering;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.NotifyHandling;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ReplayInlineCommentsStep {

  interface Factory {
    ReplayInlineCommentsStep create(Change change, ChangeInfo changeInfo,
        RemoteApi api);
  }

  private final AccountUtil accountUtil;
  private final ReviewDb db;
  private final Provider<PostReview> postReview;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Change change;
  private final ChangeInfo changeInfo;
  private final RemoteApi api;

  @Inject
  public ReplayInlineCommentsStep(AccountUtil accountUtil,
      ReviewDb db,
      Provider<PostReview> postReview,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo,
      @Assisted RemoteApi api) {
    this.accountUtil = accountUtil;
    this.db = db;
    this.postReview = postReview;
    this.genericUserFactory = genericUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.change = change;
    this.changeInfo = changeInfo;
    this.api = api;
  }

  void replay() throws RestApiException, OrmException, IOException,
      NoSuchChangeException, NoSuchAccountException {
    for (PatchSet ps : db.patchSets().byChange(change.getId())) {
      Iterable<CommentInfo> comments = api.getComments(
          changeInfo._number, ps.getRevision().get());

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

  private ChangeControl control(Change change, Account.Id id)
      throws NoSuchChangeException {
    return changeControlFactory.controlFor(change,
        genericUserFactory.create(id));
  }
}
