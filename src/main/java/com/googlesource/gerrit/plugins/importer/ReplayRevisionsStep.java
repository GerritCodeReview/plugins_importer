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

import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class ReplayRevisionsStep {

  interface Factory {
    ReplayRevisionsStep create(Repository repo, RevWalk rw, Change change,
        ChangeInfo changeInfo);
  }

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

  void replay() throws IOException, OrmException, NoSuchAccountException {
    List<RevisionInfo> revisions = new ArrayList<>(changeInfo.revisions.values());
    sortRevisionInfoByNumber(revisions);
    List<PatchSet> patchSets = new ArrayList<>();

    db.changes().beginTransaction(change.getId());
    try {
      for (RevisionInfo r : revisions) {
        String origRef = r.ref;
        ObjectId id = repo.resolve(origRef);
        if (id == null) {
          // already replayed?
          continue;
        }
        RevCommit commit = rw.parseCommit(id);

        PatchSet ps = new PatchSet(new PatchSet.Id(change.getId(), r._number));
        patchSets.add(ps);

        ps.setUploader(accountUtil.resolveUser(r.uploader));
        ps.setCreatedOn(r.created);
        ps.setRevision(new RevId(commit.name()));
        ps.setDraft(r.draft != null && r.draft);

        PatchSetInfo info = patchSetInfoFactory.get(commit, ps.getId());
        if (changeInfo.currentRevision.equals(info.getRevId())) {
          change.setCurrentPatchSet(info);
        }

        ChangeUtil.insertAncestors(db, ps.getId(), commit);

        renameRef(repo, origRef, ps);
      }

      db.patchSets().insert(patchSets);
      db.commit();
    } finally {
      db.rollback();
    }
  }

  private static void sortRevisionInfoByNumber(List<RevisionInfo> list) {
    Collections.sort(list, new Comparator<RevisionInfo>() {
      @Override
      public int compare(RevisionInfo a, RevisionInfo b) {
        return a._number - b._number;
      }
    });
  }

  private void renameRef(Repository repo, String origRef, PatchSet ps)
      throws IOException {
    String ref = ps.getId().toRefName();
    if (ref.equals(origRef)) {
      return;
    }

    createRef(repo, ps);
    deleteRef(repo, ps, origRef);
  }

  private void createRef(Repository repo, PatchSet ps) throws IOException {
    String ref = ps.getId().toRefName();
    RefUpdate ru = repo.updateRef(ref);
    ru.setForceUpdate(true);
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(ObjectId.fromString(ps.getRevision().get()));
    RefUpdate.Result result = ru.update();
    switch (result) {
      case NEW:
      case FORCED:
      case FAST_FORWARD:
        return;
      default:
        throw new IOException(String.format(
            "Failed to create ref %s, RefUpdate.Result = %s", ref, result));
    }
  }

  private void deleteRef(Repository repo, PatchSet ps, String ref)
      throws IOException {
    RefUpdate ru = repo.updateRef(ref);
    ru.setForceUpdate(true);
    ru.setExpectedOldObjectId(ObjectId.fromString(ps.getRevision().get()));
    ru.setNewObjectId(ObjectId.zeroId());
    RefUpdate.Result result = ru.update();
    switch (result) {
      case FORCED:
        return;
      default:
        throw new IOException(String.format(
            "Failed to delete ref %s, RefUpdate.Result = %s", ref, result));
    }
  }
}
