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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.account.GetSshKeys.SshKeyInfo;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.List;

interface GerritApi {

  class Factory {
    private final LocalApi localApi;

    @Inject
    Factory(LocalApi localApi) {
      this.localApi = localApi;
    }

    GerritApi create(String url, String user, String pass) {
      if (url == null) {
        return localApi;
      } else {
        return new RemoteApi(url, user, pass);
      }
    }
  }

  public ProjectInfo getProject(String projectName) throws BadRequestException,
      IOException;

  public List<ChangeInfo> queryChanges(String projectName)
      throws BadRequestException, IOException;

  public GroupInfo getGroup(String groupName) throws BadRequestException,
      IOException, OrmException;

  /**
   * Retrieves inline comments of a patch set.
   *
   * @param changeId numeric change ID
   * @param rev the revision
   * @return Iterable that provides the inline comments, or {@code null} if the
   *         revision does not exist
   * @throws IOException thrown if sending the request fails
   * @throws BadRequestException thrown if the response is neither
   *         {@code 200 OK} nor {@code 404 Not Found}
   */
  public Iterable<CommentInfo> getComments(int changeId, String rev)
      throws BadRequestException, IOException, OrmException;

  public List<SshKeyInfo> getSshKeys(String userId) throws BadRequestException,
      IOException, OrmException;
}
