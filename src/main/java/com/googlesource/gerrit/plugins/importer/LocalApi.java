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

import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.api.changes.Changes.QueryRequest;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.GetSshKeys;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LocalApi implements GerritApi {
  private final com.google.gerrit.extensions.api.GerritApi gApi;
  private final AccountsCollection accounts;
  private final GetSshKeys getSshKeys;

  @Inject
  LocalApi(
      com.google.gerrit.extensions.api.GerritApi gApi,
      AccountsCollection accounts,
      GetSshKeys getSshKeys) {
    this.gApi = gApi;
    this.accounts = accounts;
    this.getSshKeys = getSshKeys;
  }

  @Override
  public ProjectInfo getProject(String projectName) throws IOException,
      BadRequestException {
    try {
      return gApi.projects().name(projectName).get();
    } catch (RestApiException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @Override
  public List<ChangeInfo> queryChanges(String projectName, int start,
      int limit) throws IOException,
      BadRequestException {
    try {

      QueryRequest query = gApi.changes()
          .query("project:" + projectName);
      query
          .withStart(start)
          .withOptions(
              ListChangesOption.DETAILED_LABELS,
              ListChangesOption.DETAILED_ACCOUNTS,
              ListChangesOption.MESSAGES,
              ListChangesOption.CURRENT_REVISION,
              ListChangesOption.ALL_REVISIONS,
              ListChangesOption.ALL_COMMITS);
      if(limit > 0) {
        query.withLimit(limit);
      }
      return query.get();
    } catch (RestApiException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @Override
  public GroupInfo getGroup(String groupName) throws IOException,
      BadRequestException, OrmException {
    try {
      return gApi.groups().id(groupName).get();
    } catch (RestApiException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @Override
  public Iterable<CommentInfo> getComments(int changeId, String rev)
      throws IOException, OrmException, BadRequestException {
    try {
      Map<String, List<CommentInfo>> result =
          gApi.changes().id(changeId).revision(rev).comments();

      for (Map.Entry<String, List<CommentInfo>> e : result.entrySet()) {
        for (CommentInfo i : e.getValue()) {
          i.path = e.getKey();
        }
      }

      return Iterables.concat(result.values());
    } catch (RestApiException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @Override
  public List<SshKeyInfo> getSshKeys(String userId) throws BadRequestException,
      IOException, OrmException, ConfigInvalidException {
    try {
      AccountResource rsrc =
          accounts.parse(TopLevelResource.INSTANCE,
              IdString.fromDecoded(userId));
      return getSshKeys.apply(rsrc);
    } catch (ResourceNotFoundException | AuthException | PermissionBackendException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @Override
  public Version getVersion() throws BadRequestException, IOException {
    return new Version(com.google.gerrit.common.Version.getVersion());
  }
}
