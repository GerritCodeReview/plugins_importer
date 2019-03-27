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

import static com.googlesource.gerrit.plugins.importer.ProgressMonitorUtil.updateAndEnd;

import com.google.common.base.Charsets;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.importer.ImportProject.Input;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.ProgressMonitor;

@Singleton
public class ImportJson {

  private final Provider<CurrentUser> currentUser;
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  ImportJson(Provider<CurrentUser> currentUser, AccountLoader.Factory accountLoaderFactory) {
    this.currentUser = currentUser;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  public ImportProjectInfo format(Input input, ImportProjectInfo info)
      throws PermissionBackendException {
    if (info == null) {
      info = new ImportProjectInfo();
      info.from = input.from;
      info.name = input.name;
      info.parent = input.parent;
      info.imports = new ArrayList<>();
    }

    info.imports.add(createImportInfo(input.user));
    return info;
  }

  private ImportInfo createImportInfo(String remoteUser) throws PermissionBackendException {
    AccountLoader accountLoader = accountLoaderFactory.create(true);
    ImportInfo importInfo = new ImportInfo();
    importInfo.timestamp = new Timestamp(TimeUtil.nowMs());
    importInfo.user = accountLoader.get(((IdentifiedUser) currentUser.get()).getAccountId());
    importInfo.remoteUser = remoteUser;
    accountLoader.fill();
    return importInfo;
  }

  public static void persist(LockFile lockFile, ImportProjectInfo info, ProgressMonitor pm)
      throws IOException {
    pm.beginTask("Persist parameters", 1);
    String s = OutputFormat.JSON_COMPACT.newGson().toJson(info);
    try (OutputStream out = lockFile.getOutputStream()) {
      out.write(s.getBytes(Charsets.UTF_8));
      out.write('\n');
    } finally {
      lockFile.commit();
    }
    updateAndEnd(pm);
  }

  public static ImportProjectInfo parse(File f) throws IOException {
    try (FileReader r = new FileReader(f)) {
      return OutputFormat.JSON_COMPACT
          .newGson()
          .fromJson(r, new TypeToken<ImportProjectInfo>() {}.getType());
    }
  }
}
