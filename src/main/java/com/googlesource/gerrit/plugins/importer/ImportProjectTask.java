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

import static java.lang.String.format;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

class ImportProjectTask implements Runnable {

  private static Logger log = LoggerFactory.getLogger(ImportProjectTask.class);

  interface Factory {
    ImportProjectTask create(
        @Assisted("from") String from,
        @Assisted Project.NameKey name,
        @Assisted("user") String user,
        @Assisted("password") String password,
        @Assisted StringBuffer result);
  }

  private final GitRepositoryManager git;
  private final File lockRoot;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final AccountCache accountCache;
  private final CurrentUser currentUser;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeIndexer indexer;

  private final String fromGerrit;
  private final Project.NameKey name;
  private final String user;
  private final String password;
  private final StringBuffer messages;

  private Repository repo;

  @Inject
  ImportProjectTask(GitRepositoryManager git,
      @PluginData File data,
      SchemaFactory<ReviewDb> schemaFactory,
      AccountCache accountCache,
      Provider<CurrentUser> currentUser,
      IdentifiedUser.GenericFactory genericUserFactory,
      ChangeControl.GenericFactory changeControlFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      ChangeUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil,
      ChangeIndexer indexer,
      @Assisted("from") String fromGerrit,
      @Assisted Project.NameKey name,
      @Assisted("user") String user,
      @Assisted("password") String password,
      @Assisted StringBuffer messages) {
    this.git = git;
    this.lockRoot = data;
    this.schemaFactory = schemaFactory;
    this.accountCache = accountCache;
    this.currentUser = currentUser.get();
    this.genericUserFactory = genericUserFactory;
    this.changeControlFactory = changeControlFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
    this.indexer = indexer;

    this.fromGerrit = fromGerrit;
    this.name = name;
    this.user = user;
    this.password = password;
    this.messages = messages;
  }

  @Override
  public void run() {
    LockFile importing = lockForImport(name);
    if (importing == null) {
      return;
    }

    try {
      repo = openRepository();
      if (repo == null) {
        return;
      }

      try {
        setupProjectConfiguration();
        gitFetch();
        replayChanges();
      } catch (IOException | GitAPIException | OrmException
          | NoSuchAccountException | NoSuchChangeException e) {
          messages.append(format("Unable to transfer project '%s' from"
            + " source gerrit host '%s': %s. Check log for details.",
            name.get(), fromGerrit, e.getMessage()));
          log.error(format("Unable to transfer project '%s' from"
            + " source gerrit host '%s'.",
            name.get(), fromGerrit), e);
      } finally {
        repo.close();
      }
    } finally {
      importing.unlock();
      importing.commit();
    }
  }

  private LockFile lockForImport(Project.NameKey project) {
    File importStatus = new File(lockRoot, project.get());
    LockFile lockFile = new LockFile(importStatus, FS.DETECTED);
    try {
      if (lockFile.lock()) {
        return lockFile;
      } else {
        messages.append(format("Project %s is being imported from another session"
            + ", skipping", name.get()));
        return null;
      }
    } catch (IOException e1) {
      messages.append(format(
          "Error while trying to lock the project %s for import", name.get()));
      return null;
    }
  }

  private Repository openRepository() {
    try {
      git.openRepository(name);
      messages.append(format("Repository %s already exists.", name.get()));
      return null;
    } catch (RepositoryNotFoundException e) {
      // Project doesn't exist
    } catch (IOException e) {
      messages.append(e.getMessage());
      return null;
    }

    try {
      return git.createRepository(name);
    } catch(IOException e) {
      messages.append(format("Error: %s, skipping project %s", e, name.get()));
      return null;
    }
  }

  private void setupProjectConfiguration() throws IOException {
    StoredConfig config = repo.getConfig();
    config.setString("remote", "origin", "url", fromGerrit
        .concat("/")
        .concat(name.get()));
    config.setString("remote", "origin", "fetch", "+refs/*:refs/*");
    config.setString("http", null, "sslVerify", Boolean.FALSE.toString());
    config.save();
  }

  private void gitFetch() throws GitAPIException {
    CredentialsProvider cp =
        new UsernamePasswordCredentialsProvider(user, password);
    FetchResult fetchResult = Git.wrap(repo).fetch()
          .setCredentialsProvider(cp)
          .setRemote("origin")
          .call();
    messages.append(format("[INFO] Project '%s' fetched: %s",
        name.get(), fetchResult.getMessages()));
  }

  private void replayChanges() throws IOException, OrmException,
      NoSuchAccountException, NoSuchChangeException {
    List<ChangeInfo> changes =
        new RemoteApi(fromGerrit, user, password).queryChanges(name.get());
    ReviewDb db = schemaFactory.open();
    RevWalk rw = new RevWalk(repo);
    try {
      for (ChangeInfo c : changes) {
        replayChange(rw, db, c);
      }
    } finally {
      rw.release();
      db.close();
    }
  }

  private void replayChange(RevWalk rw, ReviewDb db, ChangeInfo c)
      throws IOException, OrmException, NoSuchAccountException,
      NoSuchChangeException {
    Change change = createChange(db, c);
    replayRevisions(db, rw, change, c);
    db.changes().insert(Collections.singleton(change));

    replayMessages(db, change, c);
    addApprovals(db, change, c);

    insertLinkToOriginalChange(db, change, c);

    indexer.index(db, change);
  }

  private Change createChange(ReviewDb db, ChangeInfo c) throws OrmException,
      NoSuchAccountException {
    Change.Id changeId = new Change.Id(db.nextChangeId());

    Change change =
        new Change(new Change.Key(c.changeId), changeId, resolveUser(c.owner),
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

  /**
   * @return the current patch set for the given change
   */
  private void replayRevisions(ReviewDb db, RevWalk rw, Change change,
      ChangeInfo c) throws IOException, OrmException, NoSuchAccountException {
    List<RevisionInfo> revisions = new ArrayList<>(c.revisions.values());
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

        ps.setUploader(resolveUser(r.uploader));
        ps.setCreatedOn(r.created);
        ps.setRevision(new RevId(commit.name()));
        ps.setDraft(r.draft != null && r.draft);

        PatchSetInfo info = patchSetInfoFactory.get(commit, ps.getId());
        if (c.currentRevision.equals(info.getRevId())) {
          change.setCurrentPatchSet(info);
        }

        ChangeUtil.insertAncestors(db, ps.getId(), commit);

        // TODO fetch comments:
        // GET /changes/{change-id}/revisions/{revision-id}/comments/'
        // TODO replay comments

        createRef(repo, ps);
        deleteRef(repo, ps, origRef);
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
        throw new IOException(String.format("Failed to create ref %s", ref));
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
        throw new IOException(String.format("Failed to delete ref %s", ref));
    }
  }

  private Account.Id resolveUser(AccountInfo acc) throws NoSuchAccountException {
    AccountState a = accountCache.getByUsername(acc.username);
    if (a == null) {
      throw new NoSuchAccountException(String.format("User %s not found",
          acc.username));
    }
    if (!Objects.equals(a.getAccount().getPreferredEmail(), acc.email)) {
      throw new NoSuchAccountException(String.format(
          "User %s not found: Email mismatch, expected %s but found %s",
          acc.username, acc.email, a.getAccount().getPreferredEmail()));
    }
    return a.getAccount().getId();
  }

  private void replayMessages(ReviewDb db, Change change, ChangeInfo c)
      throws IOException, NoSuchChangeException, OrmException,
      NoSuchAccountException {
    for (ChangeMessageInfo msg : c.messages) {
      Account.Id userId = resolveUser(msg.author);
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

  private void addApprovals(ReviewDb db, Change change, ChangeInfo c)
      throws OrmException, NoSuchChangeException, IOException,
      NoSuchAccountException {
    List<PatchSetApproval> approvals = new ArrayList<>();
    for (Entry<String, LabelInfo> e : c.labels.entrySet()) {
      String labelName = e.getKey();
      LabelInfo label = e.getValue();
      if (label.all != null) {
        for (ApprovalInfo a : label.all) {
          Account.Id user = resolveUser(a);
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
    db.patchSetApprovals().insert(approvals);
  }

  private void insertLinkToOriginalChange(ReviewDb db, Change change,
      ChangeInfo c) throws NoSuchChangeException, OrmException, IOException {
    insertMessage(db, change, "Imported from " + changeUrl(c));
  }

  private String changeUrl(ChangeInfo c) {
    StringBuilder url = new StringBuilder();
    url.append(ensureSlash(fromGerrit)).append(c._number);
    return url.toString();
  }

  private void insertMessage(ReviewDb db, Change change, String message)
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
