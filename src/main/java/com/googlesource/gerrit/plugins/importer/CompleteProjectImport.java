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

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import com.googlesource.gerrit.plugins.importer.CompleteProjectImport.Input;

import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;

@RequiresCapability(ImportCapability.ID)
class CompleteProjectImport implements RestModifyView<ImportProjectResource, Input> {
  public static class Input {
  }

  private final File lockRoot;

  @Inject
  CompleteProjectImport(@PluginData File data) {
    lockRoot = data;
  }

  @Override
  public Response<?> apply(ImportProjectResource rsrc, Input input) throws ResourceConflictException {
    LockFile lock = lockForDelete(rsrc.getName());
    try {
      rsrc.getImportStatus().delete();
      return Response.none();
    } finally {
      lock.unlock();
    }
  }

  private LockFile lockForDelete(Project.NameKey project) throws ResourceConflictException {
    File importStatus = new File(lockRoot, project.get());
    LockFile lockFile = new LockFile(importStatus, FS.DETECTED);
    try {
      if (lockFile.lock()) {
        return lockFile;
      } else {
        throw new ResourceConflictException(
            "project is being imported from another session");
      }
    } catch (IOException e) {
      throw new ResourceConflictException("failed to lock project for delete");
    }
  }
}
