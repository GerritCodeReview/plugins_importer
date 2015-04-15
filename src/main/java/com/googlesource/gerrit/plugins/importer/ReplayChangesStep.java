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

import com.google.common.collect.Iterators;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

class ReplayChangesStep {

  interface Factory {
    ReplayChangesStep create(
        @Nullable String fromGerrit,
        GerritApi api,
        Repository repo,
        @Assisted("srcProject") Project.NameKey srcProject,
        @Assisted("targetProject") Project.NameKey targetProject,
        @Assisted("force") boolean force,
        @Assisted("resume") boolean resume,
        ResumeImportStatistic importStatistic,
        ProgressMonitor pm);
  }

  private static Logger log = LoggerFactory.getLogger(ReplayChangesStep.class);

  private final String pluginName;
  private final ReplayRevisionsStep.Factory replayRevisionsFactory;
  private final ReplayInlineCommentsStep.Factory replayInlineCommentsFactory;
  private final ReplayMessagesStep.Factory replayMessagesFactory;
  private final AddApprovalsStep.Factory addApprovalsFactory;
  private final AddHashtagsStep.Factory addHashtagsFactory;
  private final InsertLinkToOriginalChangeStep.Factory insertLinkToOriginalFactory;
  private final AccountUtil accountUtil;
  private final ReviewDb db;
  private final ChangeIndexer indexer;
  private final Provider<InternalChangeQuery> queryProvider;
  private final String fromGerrit;
  private final GerritApi api;
  private final Repository repo;
  private final Project.NameKey srcProject;
  private final Project.NameKey targetProject;
  private final boolean force;
  private final boolean resume;
  private final ResumeImportStatistic importStatistic;
  private final ProgressMonitor pm;

  @Inject
  ReplayChangesStep(
      @PluginName String pluginName,
      ReplayRevisionsStep.Factory replayRevisionsFactory,
      ReplayInlineCommentsStep.Factory replayInlineCommentsFactory,
      ReplayMessagesStep.Factory replayMessagesFactory,
      AddApprovalsStep.Factory addApprovalsFactory,
      AddHashtagsStep.Factory addHashtagsFactory,
      InsertLinkToOriginalChangeStep.Factory insertLinkToOriginalFactory,
      AccountUtil accountUtil,
      ReviewDb db,
      ChangeIndexer indexer,
      Provider<InternalChangeQuery> queryProvider,
      @Assisted @Nullable String fromGerrit,
      @Assisted GerritApi api,
      @Assisted Repository repo,
      @Assisted("srcProject") Project.NameKey srcProject,
      @Assisted("targetProject") Project.NameKey targetProject,
      @Assisted("force") boolean force,
      @Assisted("resume") boolean resume,
      @Assisted ResumeImportStatistic importStatistic,
      @Assisted ProgressMonitor pm) {
    this.pluginName = pluginName;
    this.replayRevisionsFactory = replayRevisionsFactory;
    this.replayInlineCommentsFactory = replayInlineCommentsFactory;
    this.replayMessagesFactory = replayMessagesFactory;
    this.addApprovalsFactory = addApprovalsFactory;
    this.addHashtagsFactory = addHashtagsFactory;
    this.insertLinkToOriginalFactory = insertLinkToOriginalFactory;
    this.accountUtil = accountUtil;
    this.db = db;
    this.indexer = indexer;
    this.queryProvider = queryProvider;
    this.fromGerrit = fromGerrit;
    this.api = api;
    this.repo = repo;
    this.srcProject = srcProject;
    this.targetProject = targetProject;
    this.force = force;
    this.resume = resume;
    this.importStatistic = importStatistic;
    this.pm = pm;
  }

  void replay() throws IOException, OrmException,
      NoSuchAccountException, NoSuchChangeException, RestApiException,
      ValidationException {
    List<ChangeInfo> changes = api.queryChanges(srcProject.get());

    pm.beginTask("Replay Changes", changes.size());
    RevWalk rw = new RevWalk(repo);
    try {
      for (ChangeInfo c : changes) {
        try {
          replayChange(rw, c);
        } catch (Exception e) {
          log.error(String.format("Failed to replay change %s.",
              Url.decode(c.id)), e);
          throw e;
        }
        pm.update(1);
      }
    } finally {
      rw.release();
    }
    pm.endTask();
  }

  private void replayChange(RevWalk rw, ChangeInfo c)
      throws IOException, OrmException, NoSuchAccountException,
      NoSuchChangeException, RestApiException, ValidationException {
    if (c.status == ChangeStatus.DRAFT) {
      // no import of draft changes
      return;
    }

    Change change = resume ? findChange(c) : null;
    boolean resumeChange;
    if (change == null) {
      resumeChange = false;
      change = createChange(c);
    } else {
      resumeChange = true;
      if (!force && change.getLastUpdatedOn().equals(c.updated)) {
        // change was not modified since last import
        return;
      }
    }

    if (c.revisions.isEmpty()) {
      log.warn(String.format("[%s] Change %s has no revisions.",
          pluginName, c.id));
      return;
    }
    if (c.currentRevision == null) {
      log.warn(String.format(
          "[%s] Change %s has no current revision.",
          pluginName, c.id));
      return;
    }

    replayRevisionsFactory.create(repo, rw, change, c).replay(api);
    upsertChange(resumeChange, change, c);

    replayInlineCommentsFactory.create(change, c, api, resumeChange).replay();
    replayMessagesFactory.create(change, c, resumeChange).replay(api);
    addApprovalsFactory.create(change, c, resume).add(api);
    addHashtagsFactory.create(change, c, resumeChange).add();

    insertLinkToOriginalFactory.create(fromGerrit, change, c, resumeChange).insert();

    indexer.index(db, change);

    if (resumeChange) {
      importStatistic.numChangesUpdated++;
    } else {
      importStatistic.numChangesCreated++;
    }
  }

  private Change findChange(ChangeInfo c) throws OrmException {
    List<Change> changes = ChangeData.asChanges(
        queryProvider.get().byBranchKey(
            new Branch.NameKey(targetProject, fullName(c.branch)),
            new Change.Key(c.changeId)));
    if (changes.isEmpty()) {
      return null;
    } else {
      return db.changes().get(
          Iterators.getOnlyElement(changes.iterator()).getId());
    }
  }

  private Change createChange(ChangeInfo c) throws OrmException,
      NoSuchAccountException, BadRequestException, IOException {
    Change.Id changeId = new Change.Id(db.nextChangeId());

    Change change =
        new Change(new Change.Key(c.changeId), changeId, accountUtil.resolveUser(api, c.owner),
            new Branch.NameKey(targetProject, fullName(c.branch)), c.created);
    change.setStatus(Change.Status.forChangeStatus(c.status));
    change.setTopic(c.topic);
    change.setLastUpdatedOn(c.updated);
    return change;
  }

  private void upsertChange(boolean resumeChange, Change change, ChangeInfo c)
      throws OrmException {
    if (resumeChange) {
      change.setStatus(Change.Status.forChangeStatus(c.status));
      change.setTopic(c.topic);
      change.setLastUpdatedOn(c.updated);
    }
    db.changes().upsert(Collections.singleton(change));
  }

  private static String fullName(String branch) {
    if (branch.startsWith(Constants.R_HEADS)) {
      return branch;
    } else {
      return Constants.R_HEADS + branch;
    }
  }
}
