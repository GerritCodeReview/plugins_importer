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
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class ReplayRevisionsStep {

  interface Factory {
    ReplayRevisionsStep create(Repository repo, RevWalk rw, Change change,
        ChangeInfo changeInfo);
  }

  private static final Logger log = LoggerFactory
      .getLogger(ReplayRevisionsStep.class);

  private final AccountUtil accountUtil;
  private final ReviewDb db;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final Repository repo;
  private final RevWalk rw;
  private final Change change;
  private final ChangeInfo changeInfo;

  @Inject
  public ReplayRevisionsStep(AccountUtil accountUtil,
      ReviewDb db,
      PatchSetInfoFactory patchSetInfoFactory,
      @Assisted Repository repo,
      @Assisted RevWalk rw,
      @Assisted Change change,
      @Assisted ChangeInfo changeInfo) {
    this.accountUtil = accountUtil;
    this.db = db;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.repo = repo;
    this.rw = rw;
    this.change = change;
    this.changeInfo = changeInfo;
  }

  void replay(GerritApi api)
      throws IOException, OrmException, NoSuchAccountException,
      RestApiException, ConfigInvalidException {
    List<RevisionInfo> revisions = new ArrayList<>(changeInfo.revisions.values());
    sortRevisionInfoByNumber(revisions);
    List<PatchSet> patchSets = new ArrayList<>();

    db.changes().beginTransaction(change.getId());
    try {
      PatchSetInfo info = null;
      for (RevisionInfo r : revisions) {
        if (r.draft != null && r.draft) {
          // no import of draft patch sets
          continue;
        }
        PatchSet ps = new PatchSet(new PatchSet.Id(change.getId(), r._number));
        String newRef = ps.getId().toRefName();
        ObjectId newId = repo.resolve(newRef);
        String origRef = imported(r.ref);
        ObjectId id = repo.resolve(origRef);
        if (id == null) {
          continue;
        }
        RevCommit commit = rw.parseCommit(id);
        if (newId != null) {
          RevCommit newCommit = rw.parseCommit(newId);
          if (newCommit.equals(commit)) {
            // already replayed
            continue;
          }
          // a patch set with the same number was created both in the source
          // and in the target system
          log.warn(String.format(
              "Project %s was modified in target system: "
                  + "Skip replay revision for patch set %s.",
              change.getProject().get(), ps.getId().toString()));
          continue;
        }

        patchSets.add(ps);

        ps.setUploader(accountUtil.resolveUser(api, r.uploader));
        ps.setCreatedOn(r.created);
        ps.setRevision(new RevId(commit.name()));
        ps.setDraft(r.draft != null && r.draft);

        info = patchSetInfoFactory.get(rw, commit, ps.getId());
        if (info.getRevId().equals(changeInfo.currentRevision)) {
          change.setCurrentPatchSet(info);
        }

        updateRef(repo, ps);
      }

      if (change.currentPatchSetId() == null) {
        if (changeInfo.currentRevision != null) {
          log.warn(String.format(
              "Current revision %s of change %s not found."
              + " Setting lastest revision %s as current patch set.",
              changeInfo.currentRevision, changeInfo.id, info.getRevId()));
        } else {
          log.warn(String.format(
              "Change %s has no current revision."
              + " Setting lastest revision %s as current patch set.",
              changeInfo.id, info.getRevId()));
        }
        change.setCurrentPatchSet(info);
      }

      db.patchSets().insert(patchSets);
      db.commit();
    } finally {
      db.rollback();
    }
  }

  private static String imported(String ref) {
    return ConfigureRepositoryStep.R_IMPORTS
        + ref.substring(Constants.R_REFS.length());
  }

  private static void sortRevisionInfoByNumber(List<RevisionInfo> list) {
    list.sort((a, b) -> a._number - b._number);
  }

  private void updateRef(Repository repo, PatchSet ps)
      throws IOException {
    String ref = ps.getId().toRefName();
    RefUpdate ru = repo.updateRef(ref);
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(ObjectId.fromString(ps.getRevision().get()));
    RefUpdate.Result result = ru.update();
    switch (result) {
      case NEW:
      case FORCED:
      case FAST_FORWARD:
        return;
      case IO_FAILURE:
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case NO_CHANGE:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case RENAMED:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      default:
        throw new IOException(String.format(
            "Failed to create ref %s, RefUpdate.Result = %s", ref, result));
    }
  }
}
