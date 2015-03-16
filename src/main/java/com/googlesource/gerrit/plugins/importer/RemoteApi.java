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
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.OutputFormat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class RemoteApi {
  private final RestSession restSession;

  RemoteApi(String url, String user, String pass) {
    restSession = new RestSession(url, user, pass);
  }

  public ProjectInfo getProject(String projectName) throws IOException,
      BadRequestException {
    String endPoint = "/projects/" + projectName;
    RestResponse r = checkedGet(endPoint);
    return newGson().fromJson(r.getReader(),
        new TypeToken<ProjectInfo>() {}.getType());
  }

  public List<ChangeInfo> queryChanges(String projectName) throws IOException,
      BadRequestException {
    String endPoint =
        "/changes/?q=project:" + projectName +
        "&O=" + Integer.toHexString(ListChangesOption.toBits(
            EnumSet.of(
                ListChangesOption.DETAILED_LABELS,
                ListChangesOption.DETAILED_ACCOUNTS,
                ListChangesOption.MESSAGES,
                ListChangesOption.CURRENT_REVISION,
                ListChangesOption.ALL_REVISIONS,
                ListChangesOption.ALL_COMMITS)));
    RestResponse r = checkedGet(endPoint);
    List<ChangeInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<ChangeInfo>>() {}.getType());

    for (ChangeInfo c : result) {
      for (Map.Entry<String, RevisionInfo> e : c.revisions.entrySet()) {
        e.getValue().commit.commit = e.getKey();
      }
    }

    return result;
  }

  public Iterable<CommentInfo> getComments(int changeId, String rev)
      throws IOException, BadRequestException {
    String endPoint = "/changes/" + changeId + "/revisions/" + rev + "/comments";
    RestResponse r = checkedGet(endPoint);
    Map<String, List<CommentInfo>> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, List<CommentInfo>>>() {}.getType());
    for (Map.Entry<String, List<CommentInfo>> e : result.entrySet()) {
      for (CommentInfo i : e.getValue()) {
        i.path = e.getKey();
      }
    }
    return Iterables.concat(result.values());
  }

  private static Gson newGson() {
    return OutputFormat.JSON_COMPACT.newGson();
  }

  private RestResponse checkedGet(String endPoint) throws IOException,
      BadRequestException {
    RestResponse r = restSession.get(endPoint);
    assertOK(HttpMethod.GET, endPoint, r);
    return r;
  }

  private static void assertOK(HttpMethod method, String endPoint,
      RestResponse r) throws IOException, BadRequestException {
    if (r.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
      throw new BadRequestException(
          "invalid credentials: accessing source system failed with 401 Unauthorized");
    }
    if (r.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
      throw new BadRequestException(
          "Project not found or missing permissions for accessing the project:"
          + " accessing source system failed with 404 Not found");
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
