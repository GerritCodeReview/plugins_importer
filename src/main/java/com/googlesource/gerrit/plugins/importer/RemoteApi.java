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

import static com.google.gerrit.extensions.restapi.Url.encode;

import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.GetSshKeys.SshKeyInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

class RemoteApi implements GerritApi {

  private final RestSession restSession;

  RemoteApi(String url, String user, String pass) {
    restSession = new RestSession(url, user, pass);
  }

  @Override
  public ProjectInfo getProject(String projectName) throws IOException,
      BadRequestException {
    projectName = encode(projectName);
    String endPoint = "/projects/" + projectName;
    try (RestResponse r = checkedGet(endPoint)) {
      return newGson().fromJson(r.getReader(),
          new TypeToken<ProjectInfo>() {}.getType());
    }
  }

  @Override
  public List<ChangeInfo> queryChanges(String projectName,
      int start, int limit) throws IOException, BadRequestException {
    String endPoint =
        "/changes/?S=" +
        start + "&n=" + limit + "&q=project:" + projectName +
        "&O=" + Integer.toHexString(ListChangesOption.toBits(
            EnumSet.of(
                ListChangesOption.DETAILED_LABELS,
                ListChangesOption.DETAILED_ACCOUNTS,
                ListChangesOption.MESSAGES,
                ListChangesOption.CURRENT_REVISION,
                ListChangesOption.ALL_REVISIONS,
                ListChangesOption.ALL_COMMITS)));

    List<ChangeInfo> result;
    try (RestResponse r = checkedGet(endPoint)) {
      result = newGson().fromJson(r.getReader(),
            new TypeToken<List<ChangeInfo>>() {}.getType());
    }

    for (ChangeInfo c : result) {
      for (Map.Entry<String, RevisionInfo> e : c.revisions.entrySet()) {
        e.getValue().commit.commit = e.getKey();
      }
    }

    return result;
  }

  @Override
  public GroupInfo getGroup(String groupName) throws IOException,
      BadRequestException {
    groupName = encode(groupName);
    String endPoint = "/groups/" + groupName + "/detail";
    try (RestResponse r = checkedGet(endPoint)) {
      return newGson().fromJson(r.getReader(),
              new TypeToken<GroupInfo>() {}.getType());
    }
  }

  @Override
  public Iterable<CommentInfo> getComments(int changeId, String rev)
      throws IOException, BadRequestException {
    String endPoint = "/changes/" + changeId + "/revisions/" + rev + "/comments";
    Map<String, List<CommentInfo>> result;
    try (RestResponse r = restSession.get(endPoint)) {
      if (r.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return null;
      }
      assertOK(HttpMethod.GET, endPoint, r);
      result = newGson().fromJson(r.getReader(),
              new TypeToken<Map<String, List<CommentInfo>>>() {}.getType());
    }
    for (Map.Entry<String, List<CommentInfo>> e : result.entrySet()) {
      for (CommentInfo i : e.getValue()) {
        i.path = e.getKey();
      }
    }
    return Iterables.concat(result.values());
  }

  @Override
  public List<SshKeyInfo> getSshKeys(String userId) throws BadRequestException, IOException {
    String endPoint = "/accounts/" + userId + "/sshkeys/";
    try (RestResponse r = checkedGet(endPoint)) {
      return newGson().fromJson(r.getReader(),
          new TypeToken<List<SshKeyInfo>>() {}.getType());
    }
  }

  @Override
  public Version getVersion() throws BadRequestException, IOException {
    String endPoint = "/config/server/version";
    try (RestResponse r = checkedGet(endPoint)) {
      return new Version((String)newGson().fromJson(
          r.getReader(), new TypeToken<String>() {}.getType()));
    }
  }

  private static Gson newGson() {
    return OutputFormat.JSON_COMPACT.newGson();
  }

  private RestResponse checkedGet(String endPoint) throws IOException,
      BadRequestException {
    try {
      RestResponse r = restSession.get(endPoint);
      assertOK(HttpMethod.GET, endPoint, r);
      return r;
    } catch (UnknownHostException e) {
      throw new BadRequestException("Unknown host: " + e.getMessage());
    }
  }

  private static void assertOK(HttpMethod method, String endPoint,
      RestResponse r) throws IOException, BadRequestException {
    if (r.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
      throw new BadRequestException(
          "invalid credentials: accessing source system failed with 401 Unauthorized");
    }
    if (r.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
      throw new BadRequestException(String.format(
          "missing permissions or invalid import entity identifier: Accessing "
          + "REST endpoint %s on source system failed with 404 Not found", endPoint));
    }

    if (r.getStatusCode() < 200 || 300 <= r.getStatusCode()) {
      throw new IOException(String.format(
          "Unexpected response code for %s on %s : %s", method.name(),
          endPoint, r.getStatusCode()));
    }
  }

  private static enum HttpMethod {
    GET
  }
}
