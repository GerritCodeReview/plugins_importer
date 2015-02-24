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

import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.OutputFormat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

public class RemoteApi {
  private final RestSession restSession;

  RemoteApi(String url, String user, String pass) {
    restSession = new RestSession(url, user, pass);
  }

  public List<ChangeInfo> queryChanges(String projectName) throws IOException {
    String endPoint =
        "/changes/?q=project:" + projectName +
        "&O=" + Integer.toHexString(ListChangesOption.toBits(
            EnumSet.of(
                ListChangesOption.DETAILED_LABELS,
                ListChangesOption.DETAILED_ACCOUNTS,
                ListChangesOption.MESSAGES,
                ListChangesOption.CURRENT_REVISION,
                ListChangesOption.ALL_REVISIONS)));
    RestResponse r = restSession.get(endPoint);
    List<ChangeInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<ChangeInfo>>() {}.getType());
    return result;
  }

  private static Gson newGson() {
    return OutputFormat.JSON_COMPACT.newGson();
  }
}
