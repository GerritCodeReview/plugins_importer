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

import com.google.common.base.Objects;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
      }
      return new RemoteApi(url, user, pass);
    }
  }

  public ProjectInfo getProject(String projectName) throws BadRequestException,
      IOException;

  public List<ChangeInfo> queryChanges(String projectName, int start, int limit)
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
      IOException, OrmException, ConfigInvalidException;

  public Version getVersion() throws BadRequestException, IOException;

  class Version implements Comparable<Version> {
    final String formatted;
    final Integer major;
    final Integer minor;
    final Integer patch;
    final Integer revision;
    final String qualifier;

    Version(String formatted) {
      this.formatted = formatted;

      Matcher m = Pattern.compile("(\\d+)\\.(\\d+)(\\.(\\d+))?(\\.(\\d+))?(-(.+))?")
          .matcher(formatted);
      if (m.matches()) {
        this.major = Integer.parseInt(m.group(1));
        this.minor = Integer.parseInt(m.group(2));
        this.patch = m.group(3) != null ? Integer.parseInt(m.group(4)) : null;
        this.revision = m.group(5) != null ? Integer.parseInt(m.group(6)) : null;
        this.qualifier = m.group(7) != null ? m.group(8) : null;
      } else {
        this.major = null;
        this.minor = null;
        this.patch = null;
        this.revision = null;
        this.qualifier = null;
      }
    }

    @Override
    public int compareTo(Version o) {
      if (major == null || o.major == null) {
        // either of the compared version is not valid
        return -1;
      }
      if (Objects.equal(major, o.major)) {
        if (Objects.equal(minor, o.minor)) {
          if (Objects.equal(patch, o.patch)) {
            return 0;
          }
          if (o.patch == null) {
            return 1;
          }
          if (patch == null) {
            return -1;
          }
          return patch - o.patch;
        }
        if (o.minor == null) {
          return 1;
        }
        if (minor == null) {
          return -1;
        }
        return minor - o.minor;
      }
      return major - o.major;
    }

    @Override
    public String toString() {
      return formatted;
    }
  }
}
