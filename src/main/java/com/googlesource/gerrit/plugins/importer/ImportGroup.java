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

import static com.google.gerrit.reviewdb.client.AccountGroup.isInternalGroup;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
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
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.server.validators.GroupCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.Sequences;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.importer.ImportGroup.Input;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiresCapability(ImportCapability.ID)
class ImportGroup implements RestCollectionCreateView<ConfigResource, ImportGroupResource, Input> {
  public static class Input {
    public String from;
    public String user;
    public String pass;
    public boolean importOwnerGroup;
    public boolean importIncludedGroups;
  }

  private static Logger log = LoggerFactory.getLogger(ImportGroup.class);

  private final Config cfg;
  private final Groups groups;
  private final GroupsUpdate groupsUpdate;
  private final Sequences sequences;
  private final PersonIdent personIdent;
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
      Groups groups,
      GroupsUpdate groupsUpdate,
      Sequences sequences,
      @GerritPersonIdent PersonIdent personIdent,
      AccountUtil accountUtil,
      AccountCache accountCache,
      GroupCache groupCache,
      GroupIncludeCache groupIncludeCache,
      DynamicSet<GroupCreationValidationListener> groupCreationValidationListeners,
      GerritApi.Factory apiFactory,
      @Assisted AccountGroup.NameKey group) {
    this.cfg = cfg;
    this.groups = groups;
    this.groupsUpdate = groupsUpdate;
    this.sequences = sequences;
    this.personIdent = personIdent;
    this.accountUtil = accountUtil;
    this.groupCache = groupCache;
    this.accountCache = accountCache;
    this.groupIncludeCache = groupIncludeCache;
    this.groupCreationValidationListeners = groupCreationValidationListeners;
    this.apiFactory = apiFactory;
    this.group = group;
  }

  @Override
  public Response<String> apply(ConfigResource rsrc, IdString id, Input input)
      throws NoSuchAccountException, OrmException, IOException, RestApiException,
          ConfigInvalidException, PermissionBackendException {
    GroupInfo groupInfo;
    this.api = apiFactory.create(input.from, input.user, input.pass);
    groupInfo = api.getGroup(group.get());
    validate(input, groupInfo);
    createGroup(input, groupInfo);

    return Response.ok("OK");
  }

  private void validate(Input input, GroupInfo groupInfo)
      throws IOException, OrmException, NoSuchAccountException, RestApiException,
          ConfigInvalidException, PermissionBackendException {
    if (!isInternalGroup(new AccountGroup.UUID(groupInfo.id))) {
      throw new MethodNotAllowedException(
          String.format(
              "Group with name %s is not an internal group and cannot be imported",
              groupInfo.name));
    }
    if (getGroupByUUID(groupInfo.id) != null) {
      throw new ResourceConflictException(
          String.format("Group with UUID %s already exists", groupInfo.id));
    }
    if (!groupInfo.id.equals(groupInfo.ownerId))
      if (!input.importOwnerGroup && getGroupByUUID(groupInfo.ownerId) == null) {
        throw new PreconditionFailedException(
            String.format(
                "Owner group %s with UUID %s does not exist",
                getGroupName(groupInfo.ownerId), groupInfo.ownerId));
      }
    if (groupInfo.members != null) {
      for (AccountInfo member : groupInfo.members) {
        try {
          accountUtil.resolveUser(api, member);
        } catch (NoSuchAccountException e) {
          throw new PreconditionFailedException(e.getMessage());
        }
      }
    }
    if (!input.importIncludedGroups) {
      if (groupInfo.includes != null) {
        for (GroupInfo include : groupInfo.includes) {
          if (getGroupByUUID(include.id) == null) {
            throw new PreconditionFailedException(
                String.format(
                    "Included group %s with UUID %s does not exist",
                    getGroupName(include.id), include.id));
          }
        }
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

  private Optional<InternalGroup> getGroupByName(String groupName) {
    return groupCache.get(new AccountGroup.NameKey(groupName));
  }

  private Optional<InternalGroup> getGroupByUUID(String uuid) {
    return groupCache.get(new AccountGroup.UUID(uuid));
  }

  private CreateGroupArgs toCreateGroupArgs(GroupInfo groupInfo)
      throws IOException, OrmException, NoSuchAccountException, RestApiException,
          ConfigInvalidException, PermissionBackendException {
    CreateGroupArgs args = new CreateGroupArgs();
    args.setGroupName(groupInfo.name);
    args.groupDescription = groupInfo.description;
    args.visibleToAll = cfg.getBoolean("groups", "newGroupsVisibleToAll", false);
    if (!groupInfo.ownerId.equals(groupInfo.id)) {
      args.ownerGroupUuid = getGroupByUUID(groupInfo.ownerId).get().getGroupUUID();
    }
    Set<Account.Id> initialMembers = new HashSet<>();
    for (AccountInfo member : groupInfo.members) {
      initialMembers.add(accountUtil.resolveUser(api, member));
    }
    args.initialMembers = initialMembers;
    return args;
  }

  private void createGroup(Input input, GroupInfo info)
      throws OrmException, NoSuchAccountException, IOException, RestApiException,
          ConfigInvalidException, PermissionBackendException {
    String uniqueName = getUniqueGroupName(info.name);
    if (!info.name.equals(uniqueName)) {
      log.warn(
          String.format(
              "Group %s with UUID %s is imported with name %s", info.name, info.id, uniqueName));
      info.name = uniqueName;
    }
    
    // See if this group name is already taken.
    boolean groupAlreadyExists = false;
    Optional<GroupReference> existingGroup = groups.getAllGroupReferences().filter(g -> g.getName().equals(info.name)).findFirst();
    if (existingGroup.isPresent()) {
      throw new ResourceConflictException(info.name);
    }

    CreateGroupArgs createGroupArgs = toCreateGroupArgs(info);

    // Note: this block is mostly copied from the REST API for creating a group.
    AccountGroup.Id groupId = new AccountGroup.Id(sequences.nextGroupId());
    AccountGroup.UUID uuid =
        GroupUUID.make(
            createGroupArgs.getGroupName(),
            personIdent);
    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(uuid)
            .setNameKey(createGroupArgs.getGroup())
            .setId(groupId)
            .build();
    InternalGroupUpdate.Builder groupUpdateBuilder =
        InternalGroupUpdate.builder().setVisibleToAll(createGroupArgs.visibleToAll);
    if (createGroupArgs.ownerGroupUuid != null) {
      Optional<InternalGroup> ownerGroup = groupCache.get(createGroupArgs.ownerGroupUuid);
      ownerGroup.map(InternalGroup::getGroupUUID).ifPresent(groupUpdateBuilder::setOwnerGroupUUID);
    }
    if (createGroupArgs.groupDescription != null) {
      groupUpdateBuilder.setDescription(createGroupArgs.groupDescription);
    }
    groupUpdateBuilder.setMemberModification(
        members -> ImmutableSet.copyOf(createGroupArgs.initialMembers));

    InternalGroup newGroup;
    try {
      newGroup = groupsUpdate.createGroup(groupCreation, groupUpdateBuilder.build());
    } catch (OrmDuplicateKeyException e) {
      throw new ResourceConflictException(
          "group '" + createGroupArgs.getGroupName() + "' already exists");
    }
    // End note.

    groupCache.evict(newGroup.getGroupUUID());
    groupCache.evict(newGroup.getId());
    groupCache.evict(newGroup.getNameKey());

    // TODO: WHAT IS THIS SUPPOSED TO DO? addMembers(group.getId(), info.members);
    // TODO: WHAT IS THIS SUPPOSED TO DO? addGroups(input, group.getId(), info.name, info.includes);

    groupCache.evict(newGroup.getGroupUUID());
    groupCache.evict(newGroup.getId());
    groupCache.evict(newGroup.getNameKey());
  }

  private String getUniqueGroupName(String name) {
    return getUniqueGroupName(name, false);
  }

  private String getUniqueGroupName(String name, boolean appendIndex) {
    if (getGroupByName(name) == null) {
      return name;
    }
    if (appendIndex) {
      int i = 0;
      while (true) {
        String groupName = String.format("%s-%d", name, ++i);
        if (getGroupByName(groupName) == null) {
          return groupName;
        }
      }
    }
    return getUniqueGroupName(String.format("%s_imported", name), true);
  }

  /* TODO WHAT IS THIS SUPPOSED TO DO?
  private void addMembers(AccountGroup.Id groupId, List<AccountInfo> members)
      throws OrmException, NoSuchAccountException, IOException, RestApiException,
          ConfigInvalidException {
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
  */

  /* TODO WHAT IS THIS SUPPOSED TO DO?
  private void addGroups(
      Input input, AccountGroup.Id groupId, String groupName, List<GroupInfo> includedGroups)
      throws NoSuchAccountException, OrmException, IOException, RestApiException,
          ConfigInvalidException {
    List<AccountGroupById> includeList = new ArrayList<>();
    for (GroupInfo includedGroup : includedGroups) {
      if (isInternalGroup(new AccountGroup.UUID(includedGroup.id))) {
        if (getGroupByUUID(includedGroup.id) == null) {
          String includedGroupName = getGroupName(includedGroup.id);
          if (input.importIncludedGroups) {
            this.apply(new ConfigResource(), IdString.fromDecoded(includedGroupName), input);
          } else {
            throw new IllegalStateException(
                String.format(
                    "Cannot include non-existing group %s into group %s.",
                    includedGroupName, groupName));
          }
        }
      }
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
  */

  private String getGroupName(String uuid) throws BadRequestException, IOException, OrmException {
    return api.getGroup(uuid).name;
  }
}
