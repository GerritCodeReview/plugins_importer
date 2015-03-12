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

import static com.google.gerrit.server.PatchLineCommentsUtil.setCommentRevId;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class ReplayInlineCommentsStep {

  interface Factory {
    ReplayInlineCommentsStep create(Change change, ChangeInfo changeInfo,
        RemoteApi api);
  }

  private final AccountUtil accountUtil;
  private final ReviewDb db;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeUpdate.Factory updateFactory;
  private final PatchLineCommentsUtil plcUtil;
  private final PatchListCache patchListCache;
  private final Change change;
  private final ChangeInfo changeInfo;
  private final RemoteApi api;

  @Inject
  public ReplayInlineCommentsStep(AccountUtil accountUtil,
      ReviewDb db,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeUpdate.Factory updateFactory,
      PatchLineCommentsUtil plcUtil,
      PatchListCache patchListCache,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo,
      @Assisted RemoteApi api) {
    this.accountUtil = accountUtil;
    this.db = db;
    this.genericUserFactory = genericUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.updateFactory = updateFactory;
    this.plcUtil = plcUtil;
    this.patchListCache = patchListCache;
    this.change = change;
    this.changeInfo = changeInfo;
    this.api = api;
  }

  void replay() throws RestApiException, OrmException, IOException,
      NoSuchChangeException, NoSuchAccountException {
    for (PatchSet ps : db.patchSets().byChange(change.getId())) {
      Iterable<CommentInfo> comments = api.getComments(
          changeInfo._number, ps.getRevision().get());

      Multimap<Account.Id, CommentInfo> commentsByAuthor = ArrayListMultimap.create();
      for (CommentInfo comment : comments) {
        Account.Id id  = accountUtil.resolveUser(comment.author);
        commentsByAuthor.put(id, comment);
      }

      for (Account.Id id : commentsByAuthor.keySet()) {
        insertComments(ps, id, commentsByAuthor.get(id));
      }
    }
  }

  private boolean insertComments(PatchSet ps, Account.Id author,
      Collection<CommentInfo> comments) throws OrmException, IOException,
      NoSuchChangeException {
    ChangeControl ctrl = control(change, author);

    Map<String, PatchLineComment> drafts = scanDraftComments(ctrl, ps);

    List<PatchLineComment> del = Lists.newArrayList();
    List<PatchLineComment> ups = Lists.newArrayList();

    for (CommentInfo c : comments) {
      String parent = Url.decode(c.inReplyTo);
      PatchLineComment e = drafts.remove(Url.decode(c.id));
      if (e == null) {
        e = new PatchLineComment(
            new PatchLineComment.Key(
                new Patch.Key(ps.getId(), c.path),
                Url.decode(c.id)),
            c.line != null ? c.line : 0,
            author, parent, c.updated);
      } else if (parent != null) {
        e.setParentUuid(parent);
      }
      e.setStatus(PatchLineComment.Status.PUBLISHED);
      e.setWrittenOn(c.updated);
      e.setSide(c.side == Side.PARENT ? (short) 0 : (short) 1);
      setCommentRevId(e, patchListCache, change, ps);
      e.setMessage(c.message);
      if (c.range != null) {
        e.setRange(new CommentRange(
            c.range.startLine,
            c.range.startCharacter,
            c.range.endLine,
            c.range.endCharacter));
        e.setLine(c.range.endLine);
      }
      ups.add(e);
    }

    del.addAll(drafts.values());
    ChangeUpdate update = updateFactory.create(ctrl, TimeUtil.nowTs());
    update.setPatchSetId(ps.getId());
    plcUtil.deleteComments(db, update, del);
    plcUtil.upsertComments(db, update, ups);
    update.commit();

    db.changes().update(Collections.singleton(change));

    return !del.isEmpty() || !ups.isEmpty();
  }

  private Map<String, PatchLineComment> scanDraftComments(ChangeControl ctrl,
      PatchSet ps) throws OrmException {
    Map<String, PatchLineComment> drafts = Maps.newHashMap();
    for (PatchLineComment c : plcUtil.draftByPatchSetAuthor(db, ps.getId(),
        ((IdentifiedUser) ctrl.getCurrentUser()).getAccountId(),
        ctrl.getNotes())) {
      drafts.put(c.getKey().get(), c);
    }
    return drafts;
  }

  private ChangeControl control(Change change, Account.Id id)
      throws NoSuchChangeException {
    return changeControlFactory.controlFor(change,
        genericUserFactory.create(id));
  }
}
