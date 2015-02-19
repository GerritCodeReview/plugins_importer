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

import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;

public class ChangeQuery extends RemoteQuery {

  private final String QUERY_PREFIX = "?q=";
  private final String DETAIL_SUFFIX =
      "&o=DETAILED_LABELS&o=DETAILED_ACCOUNTS&o=MESSAGES";

  ChangeQuery(String url, String user, String pass) {
    super(url, user, pass);
  }

  public List<ChangeInfo> query(String projectName) throws IOException {
    String endPoint =
        "/changes/" + QUERY_PREFIX + "project:" + projectName + DETAIL_SUFFIX;
    RestResponse r = getRestSession().get(endPoint);
    List<ChangeInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<ChangeInfo>>() {}.getType());
    return result;
  }
}
