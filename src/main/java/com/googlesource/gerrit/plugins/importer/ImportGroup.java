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

import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gerrit.server.validators.GroupCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import com.googlesource.gerrit.plugins.importer.ImportGroup.Input;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequiresCapability(ImportCapability.ID)
class ImportGroup implements RestModifyView<ConfigResource, Input> {
  public static class Input {
    public String from;
    public String user;
    public String pass;
  }

  interface Factory {
    ImportGroup create(AccountGroup.NameKey group);
  }

  private final Config cfg;
  private final ReviewDb db;
  private final AccountUtil accountUtil;
  private final AccountCache accountCache;
  private final GroupCache groupCache;
  private final GroupIncludeCache groupIncludeCache;
  private final DynamicSet<GroupCreationValidationListener> groupCreationValidationListeners;
  private final GerritApi.Factory apiFactory;
  private final AccountGroup.NameKey group;
  private GerritApi api;

  @Inject
  ImportGroup(
      @GerritServerConfig Config cfg,
      ReviewDb db,
      AccountUtil accountUtil,
      AccountCache accountCache,
      GroupCache groupCache,
      GroupIncludeCache groupIncludeCache,
      DynamicSet<GroupCreationValidationListener> groupCreationValidationListeners,
      GerritApi.Factory apiFactory,
      @Assisted AccountGroup.NameKey group) {
    this.db = db;
    this.accountUtil = accountUtil;
    this.groupCache = groupCache;
    this.accountCache = accountCache;
    this.groupIncludeCache = groupIncludeCache;
    this.groupCreationValidationListeners = groupCreationValidationListeners;
    this.cfg = cfg;
    this.apiFactory = apiFactory;
    this.group = group;
  }

  @Override
  public Response<String> apply(ConfigResource rsrc, Input input)
      throws ResourceConflictException, PreconditionFailedException,
      BadRequestException, NoSuchAccountException, OrmException, IOException {
    GroupInfo groupInfo;
    this.api = apiFactory.create(input.from, input.user, input.pass);
    groupInfo = api.getGroup(group.get());
    validate(groupInfo);
    createGroup(groupInfo);

    return Response.<String> ok("OK");
  }

  private void validate(GroupInfo groupInfo) throws ResourceConflictException,
      PreconditionFailedException, BadRequestException, IOException,
      OrmException, NoSuchAccountException {
    if (getGroupByName(groupInfo.name) != null) {
      throw new ResourceConflictException(String.format(
          "Group with name %s already exists", groupInfo.name));
    }
    if (getGroupByUUID(groupInfo.id) != null) {
      throw new ResourceConflictException(String.format(
          "Group with UUID %s already exists", groupInfo.id));
    }
    if (!groupInfo.id.equals(groupInfo.ownerId))
      if (getGroupByUUID(groupInfo.ownerId) == null) {
        throw new PreconditionFailedException(String.format(
            "Owner group with UUID %s does not exist", groupInfo.ownerId));
      }
    for (AccountInfo member : groupInfo.members) {
      try {
        accountUtil.resolveUser(api, member);
      } catch (NoSuchAccountException e) {
        throw new PreconditionFailedException(e.getMessage());
      }
    }
    for (GroupInfo include : groupInfo.includes) {
      if (getGroupByUUID(include.id) == null) {
        throw new PreconditionFailedException(String.format(
            "Included group with UUID %s does not exist", include.id));
      }
    }

    for (GroupCreationValidationListener l : groupCreationValidationListeners) {
      try {
        l.validateNewGroup(toCreateGroupArgs(groupInfo));
      } catch (ValidationException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }
    }
  }

  private AccountGroup getGroupByName(String groupName) {
    return groupCache.get(new AccountGroup.NameKey(groupName));
  }

  private AccountGroup getGroupByUUID(String uuid) {
    return groupCache.get(new AccountGroup.UUID(uuid));
  }

  private CreateGroupArgs toCreateGroupArgs(GroupInfo groupInfo)
      throws IOException, OrmException, BadRequestException,
      NoSuchAccountException {
    CreateGroupArgs args = new CreateGroupArgs();
    args.setGroupName(groupInfo.name);
    args.groupDescription = groupInfo.description;
    args.visibleToAll = cfg.getBoolean("groups", "newGroupsVisibleToAll", false);
    if (!groupInfo.ownerId.equals(groupInfo.id)) {
      args.ownerGroupId = getGroupByUUID(groupInfo.ownerId).getId();
    }
    Set<Account.Id> initialMembers = new HashSet<>();
    for (AccountInfo member : groupInfo.members) {
      initialMembers.add(accountUtil.resolveUser(api, member));
    }
    args.initialMembers = initialMembers;
    Set<AccountGroup.UUID> initialGroups = new HashSet<>();
    for (GroupInfo member : groupInfo.includes) {
      initialGroups.add(new AccountGroup.UUID(member.id));
    }
    args.initialGroups = initialGroups;
    return args;
  }

  private AccountGroup createGroup(GroupInfo info) throws OrmException,
      ResourceConflictException, NoSuchAccountException, BadRequestException,
      IOException {
    AccountGroup.Id groupId = new AccountGroup.Id(db.nextAccountGroupId());
    AccountGroup.UUID uuid = new AccountGroup.UUID(info.id);
    AccountGroup group =
        new AccountGroup(new AccountGroup.NameKey(info.name), groupId, uuid);
    group.setVisibleToAll(cfg.getBoolean("groups", "newGroupsVisibleToAll",
        false));
    group.setDescription(info.description);
    group.setOwnerGroupUUID(new AccountGroup.UUID(info.ownerId));
    AccountGroupName gn = new AccountGroupName(group);
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    try {
      db.accountGroupNames().insert(Collections.singleton(gn));
    } catch (OrmDuplicateKeyException e) {
      throw new ResourceConflictException(info.name);
    }
    db.accountGroups().insert(Collections.singleton(group));

    addMembers(groupId, info.members);
    addGroups(groupId, info.includes);

    groupCache.evict(group);

    return group;
  }

  private void addMembers(AccountGroup.Id groupId, List<AccountInfo> members)
      throws OrmException, NoSuchAccountException, BadRequestException,
      IOException {
    List<AccountGroupMember> memberships = new ArrayList<>();
    for (AccountInfo member : members) {
      Account.Id userId = accountUtil.resolveUser(api, member);
      AccountGroupMember membership =
          new AccountGroupMember(new AccountGroupMember.Key(userId, groupId));
      memberships.add(membership);
    }
    db.accountGroupMembers().insert(memberships);

    for (AccountInfo member : members) {
      accountCache.evict(accountUtil.resolveUser(api, member));
    }
  }

  private void addGroups(AccountGroup.Id groupId, List<GroupInfo> includedGroups)
      throws OrmException {
    List<AccountGroupById> includeList = new ArrayList<>();
    for (GroupInfo includedGroup : includedGroups) {
      AccountGroup.UUID memberUUID = new AccountGroup.UUID(includedGroup.id);
      AccountGroupById groupInclude =
          new AccountGroupById(new AccountGroupById.Key(groupId, memberUUID));
      includeList.add(groupInclude);
    }
    db.accountGroupById().insert(includeList);

    for (GroupInfo member : includedGroups) {
      groupIncludeCache.evictParentGroupsOf(new AccountGroup.UUID(member.id));
    }
  }

}
