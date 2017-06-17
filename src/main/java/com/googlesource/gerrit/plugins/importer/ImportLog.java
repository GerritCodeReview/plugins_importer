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

import com.google.common.base.Throwables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.util.PluginLogFile;
import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

@Singleton
class ImportLog extends PluginLogFile {
  private static final String IMPORT_LOG_NAME = "import_log";
  private static final Logger log = LogManager.getLogger(IMPORT_LOG_NAME);

  public static String ACCOUNT_ID = "accountId";
  public static String USER_NAME = "userName";
  public static String FROM = "from";
  public static String SRC_PROJECT_NAME = "srcProjectName";
  public static String TARGET_PROJECT_NAME = "targetProjectName";
  public static String ERROR = "error";

  private final AuditService auditService;
  private final String canonicalWebUrl;

  @Inject
  public ImportLog(SystemLog systemLog,
      ServerInformation serverInfo,
      AuditService auditService,
      @CanonicalWebUrl String canonicalWebUrl) {
    super(systemLog, serverInfo, IMPORT_LOG_NAME, new ImportLogLayout());
    this.auditService = auditService;
    this.canonicalWebUrl = canonicalWebUrl;
  }

  public void onImport(IdentifiedUser user, Project.NameKey srcProject,
      Project.NameKey targetProject, String from) {
    onImport(user, srcProject, targetProject, from, null);
  }

  public void onImport(IdentifiedUser user, Project.NameKey srcProject,
      Project.NameKey targetProject, String from, Exception ex) {
    long ts = TimeUtil.nowMs();
    LoggingEvent event = new LoggingEvent( //
        Logger.class.getName(), // fqnOfCategoryClass
        log, // logger
        ts, // when
        ex == null // level
            ? Level.INFO
            : Level.ERROR,
        ex == null // message text
            ? "OK"
            : "FAIL",
        Thread.currentThread().getName(), // thread name
        null, // exception information
        null, // current NDC string
        null, // caller location
        null // MDC properties
        );

    event.setProperty(ACCOUNT_ID, user.getAccountId().toString());
    event.setProperty(USER_NAME, user.getUserName());

    if (from != null) {
      event.setProperty(FROM, from);
    } else {
      event.setProperty(FROM, canonicalWebUrl);
    }

    event.setProperty(SRC_PROJECT_NAME, srcProject.get());
    event.setProperty(TARGET_PROJECT_NAME, targetProject.get());

    if (ex != null) {
      event.setProperty(ERROR, Throwables.getStackTraceAsString(ex));
    }

    log.callAppenders(event);

    audit(user, ts, srcProject, from, ex);
  }

  private void audit(IdentifiedUser user, long ts, Project.NameKey project,
      String from, Exception ex) {
    ListMultimap<String, Object> params =
        MultimapBuilder.hashKeys().arrayListValues().build();
    params.put("class", ImportLog.class);
    params.put("project", project.get());
    params.put("from", from);

    auditService.dispatch(
        new AuditEvent(
            null, // sessionId
            user, // who
            ex == null // what
                ? "ProjectImport"
                : "ProjectImportFailure",
            ts, // when
            params, // params
            ex != null // result
                ? ex.toString()
                : "OK"
        ));
  }
}
