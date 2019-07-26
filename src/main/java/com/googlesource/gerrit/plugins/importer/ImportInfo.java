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

import com.google.common.base.MoreObjects;
import com.google.gerrit.extensions.common.AccountInfo;
import java.sql.Timestamp;

public class ImportInfo {
  public Timestamp timestamp;
  public AccountInfo user;
  public String remoteUser;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("timestamp", timestamp)
        .add("user", stringify(user))
        .add("remoteUser", remoteUser)
        .toString();
  }

  private String stringify(AccountInfo user) {
    return MoreObjects.toStringHelper(user)
        .add("_accountId", user._accountId)
        .add("name", user.name)
        .add("email", user.email)
        .add("username", user.username)
        .add("status", user.status)
        .toString();
  }
}
