//Copyright (C) 2015 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import static java.lang.String.format;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import com.googlesource.gerrit.plugins.importer.ProjectRestEndpoint.Input;

import java.util.ArrayList;
import java.util.List;

@RequiresCapability(ImportCapability.ID)
@Singleton
class ProjectRestEndpoint implements RestModifyView<ConfigResource, Input>,
    LifecycleListener {
  public static class Input {
    public String from;
    public String user;
    public String pass;
    public List<String> projects;
    public String parent;
  }

  // TODO: this should go into the plugin configuration.
  private final static int maxNumberOfImporterThreads = 20;

  private final WorkQueue queue;
  private final ImportProjectTask.Factory importFactory;
  private final ThreadLocalRequestContext tl;
  private final Provider<CurrentUser> currentUser;
  private final SchemaFactory<ReviewDb> schemaFactory;

  private WorkQueue.Executor executor;
  private ListeningExecutorService pool;

  @Inject
  ProjectRestEndpoint(WorkQueue queue,
      ImportProjectTask.Factory importFactory,
      ThreadLocalRequestContext tl,
      Provider<CurrentUser> currentUser,
      SchemaFactory<ReviewDb> schemaFactory) {
    this.queue = queue;
    this.importFactory = importFactory;
    this.tl = tl;
    this.currentUser = currentUser;
    this.schemaFactory = schemaFactory;
  }

  @Override
  public String apply(ConfigResource rsrc, Input input) throws OrmException {
    long startTime = System.currentTimeMillis();
    StringBuffer result = new StringBuffer();

    List<ListenableFuture<?>> tasks = new ArrayList<>();
    for(String projectName : input.projects) {
      Project.NameKey name = new Project.NameKey(projectName);
      Project.NameKey parent = input.parent != null
          ? new Project.NameKey(input.parent) : null;
      Runnable task = importFactory.create(input.from, name, parent,
          input.user, input.pass, result);
      tasks.add(pool.submit(withRequestContext(task)));
    }
    Futures.getUnchecked(Futures.allAsList(tasks));
    // TODO: the log message below does not take the failed imports into account.
    result.append(format("[INFO] %d projects imported in %d milliseconds.%n",
        input.projects.size(), (System.currentTimeMillis() - startTime)));
    return result.toString();
  }

  @Override
  public void start() {
    executor = queue.createQueue(maxNumberOfImporterThreads, "ProjectImporter");
    pool = MoreExecutors.listeningDecorator(executor);
  }

  @Override
  public void stop() {
    if (executor != null) {
      executor.shutdown();
      executor.unregisterWorkQueue();
      executor = null;
      pool = null;
    }
  }

  private Runnable withRequestContext(final Runnable task)
      throws OrmException {
    final ReviewDb db = schemaFactory.open();
    final CurrentUser user = currentUser.get();
    return new Runnable() {
      @Override
      public void run() {
        RequestContext old = tl.setContext(new RequestContext() {
          @Override

          public CurrentUser getCurrentUser() {
            return user;
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return new Provider<ReviewDb>() {
              @Override
              public ReviewDb get() {
                return db;
              }
            };
          }
        });
        try {
          task.run();
        } finally {
          tl.setContext(old);
          db.close();
        }
      }
    };
  }
}
