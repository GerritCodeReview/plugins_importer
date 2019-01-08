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

import static java.lang.String.format;

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Singleton
@RequiresCapability(ImportCapability.ID)
public class ProjectsCollection implements ChildCollection<ConfigResource, ImportProjectResource> {

  class FileSystemLayout {

    private String SUFFIX_IMPORT_STATUS_FILE = ".$importstatus";

    public File getLockRoot() {
      return lockRoot;
    }

    File getImportStatusFile(String id) {
      return new File(lockRoot, format("%s%s", id, SUFFIX_IMPORT_STATUS_FILE));
    }

    String resolveProjectName(File f) throws IOException {
      if (!f.isFile()) {
        throw new RuntimeException(
            format(
                "'%s' is not a file. Project names"
                    + "can only be resolved for existing files, not for directories.",
                f));
      }

      if (!f.getName().endsWith(SUFFIX_IMPORT_STATUS_FILE)) {
        throw new RuntimeException(
            format(
                "'%s' is not a valid import status" + "file. Invalid appendix. Should be '%s'.",
                f, SUFFIX_IMPORT_STATUS_FILE));
      }
      String diff = diff(lockRoot, f);
      return diff.substring(0, diff.length() - SUFFIX_IMPORT_STATUS_FILE.length());
    }

    /**
     * Returns the path between two file instances. The child instance is expected to be a child of
     * the parent instance.
     *
     * @param parent
     * @param child
     * @return The path between parent and child.
     * @throws IOException in case <code>child</code> is not a child of <code>parent</code>.
     */
    private String diff(File parent, File child) throws IOException {
      Path parentPath = parent.getAbsoluteFile().toPath();
      Path childPath = child.getAbsoluteFile().toPath();
      if (childPath.startsWith(parentPath)) {
        return parentPath.relativize(childPath).toString();
      }
      throw new IOException(String.format("'%s' is not a child of '%s'.", child, parent));
    }
  }

  public final FileSystemLayout FS_LAYOUT = new FileSystemLayout();

  private final DynamicMap<RestView<ImportProjectResource>> views;
  private final Provider<ListImportedProjects> list;
  private final File lockRoot;

  @Inject
  ProjectsCollection(
      DynamicMap<RestView<ImportProjectResource>> views,
      Provider<ListImportedProjects> list,
      @PluginData File data) {
    this.views = views;
    this.list = list;
    this.lockRoot = data;
  }

  @Override
  public RestView<ConfigResource> list() {
    return list.get();
  }

  @Override
  public ImportProjectResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException {
    return parse(id.get());
  }

  public ImportProjectResource parse(String id) throws ResourceNotFoundException {
    File f = FS_LAYOUT.getImportStatusFile(id);
    if (!f.exists()) {
      throw new ResourceNotFoundException(id);
    }

    return new ImportProjectResource(id, f);
  }

  @Override
  public DynamicMap<RestView<ImportProjectResource>> views() {
    return views;
  }
}
