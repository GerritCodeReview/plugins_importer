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

import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;
import static com.googlesource.gerrit.plugins.importer.ImportProjectResource.IMPORT_PROJECT_KIND;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.config.FactoryModule;
import com.google.inject.internal.UniqueAnnotations;

class Module extends FactoryModule {
  @Override
  protected void configure() {
    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(ImportCapability.ID))
        .to(ImportCapability.class);
    install(new RestApiModule() {
      @Override
      protected void configure() {
        DynamicMap.mapOf(binder(), IMPORT_PROJECT_KIND);
        child(CONFIG_KIND, "projects").to(ProjectsCollection.class);
        get(IMPORT_PROJECT_KIND).to(GetImportedProject.class);
      }
    });
    bind(LifecycleListener.class)
      .annotatedWith(UniqueAnnotations.create())
      .to(ImportLog.class);
    bind(OpenRepositoryStep.class);
    bind(ConfigureRepositoryStep.class);
    bind(ConfigureProjectStep.class);
    bind(GitFetchStep.class);
    bind(AccountUtil.class);
    factory(ImportProject.Factory.class);
    factory(ReplayChangesStep.Factory.class);
    factory(ReplayRevisionsStep.Factory.class);
    factory(ReplayInlineCommentsStep.Factory.class);
    factory(ReplayMessagesStep.Factory.class);
    factory(AddApprovalsStep.Factory.class);
    factory(AddHashtagsStep.Factory.class);
    factory(InsertLinkToOriginalChangeStep.Factory.class);
  }
}
