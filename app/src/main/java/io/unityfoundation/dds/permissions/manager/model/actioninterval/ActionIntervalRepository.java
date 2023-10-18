// Copyright 2023 DDS Permissions Manager Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package io.unityfoundation.dds.permissions.manager.model.actioninterval;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import io.unityfoundation.dds.permissions.manager.model.group.Group;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionIntervalRepository extends PageableRepository<ActionInterval, Long> {
    Page<ActionInterval> findAllByPermissionsGroupIdIn(List<Long> groupIds, Pageable pageable);

    Page<ActionInterval> findAllByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(String name, String groupName, Pageable pageable);

    List<Long> findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(String name, String groupName);

    Page<ActionInterval> findAllByIdInAndPermissionsGroupIdIn(List<Long> actionIntervalIds, List<Long> groupIds, Pageable pageable);

    Optional<ActionInterval> findByNameAndPermissionsGroup(String name, Group group);

    Optional<ActionInterval> findByIdAndPermissionsGroupId(Long actionIntervalId, Long groupId);
}