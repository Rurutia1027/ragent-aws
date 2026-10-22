/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mzt.logapi.starter.annotation.LogRecord;
import com.nageoffer.ai.ragent.audit.constant.BizChangeBizType;
import com.nageoffer.ai.ragent.audit.constant.BizChangeOperationType;
import com.nageoffer.ai.ragent.audit.support.BizChangeLogContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.controller.request.AgentSkillPageRequest;
import com.nageoffer.ai.ragent.rag.controller.request.AgentSkillSaveRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.AgentSkillToolOptionVO;
import com.nageoffer.ai.ragent.rag.controller.vo.AgentSkillVO;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNodeRegistry;
import com.nageoffer.ai.ragent.rag.core.skill.AgentSkillCacheManager;
import com.nageoffer.ai.ragent.rag.dao.entity.AgentSkillDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.AgentSkillMapper;
import com.nageoffer.ai.ragent.rag.service.AgentSkillAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentSkillAdminServiceImpl implements AgentSkillAdminService {

    /**
     * The skill id is the name the model reports when loading the body. It must be a snake_case string starting with a lowercase letter; Chinese and spaces are reported unreliably by the model
     */
    private static final Pattern SKILL_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    /**
     * Matches the column widths of t_agent_skill
     */
    private static final int NAME_MAX_LENGTH = 64;
    private static final int DESCRIPTION_MAX_LENGTH = 512;

    private final AgentSkillMapper agentSkillMapper;
    private final AgentSkillCacheManager cacheManager;
    private final IntentNodeRegistry intentNodeRegistry;
    private final BizChangeLogContext bizChangeLogContext;

    @Override
    public IPage<AgentSkillVO> pageQuery(AgentSkillPageRequest requestParam) {
        String keyword = StrUtil.trimToNull(requestParam.getKeyword());
        Page<AgentSkillDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<AgentSkillDO> result = agentSkillMapper.selectPage(
                page,
                Wrappers.lambdaQuery(AgentSkillDO.class)
                        .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                                .like(AgentSkillDO::getSkillCode, keyword)
                                .or()
                                .like(AgentSkillDO::getName, keyword)
                                .or()
                                .like(AgentSkillDO::getDescription, keyword))
                        .orderByAsc(AgentSkillDO::getSortOrder)
                        .orderByDesc(AgentSkillDO::getUpdateTime)
        );
        return result.convert(record -> toVO(record, false));
    }

    @Override
    public AgentSkillVO queryById(String id) {
        return toVO(loadById(id), true);
    }

    @Override
    @LogRecord(
            success = "创建技能：{{#requestParam.name}}",
            fail = "创建技能失败：{{#_errorMsg}}",
            type = BizChangeBizType.AGENT_SKILL,
            subType = BizChangeOperationType.CREATE,
            bizNo = BizChangeLogContext.BIZ_ID_EXPRESSION,
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public String create(AgentSkillSaveRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("Request cannot be empty"));
        String skillCode = normalizeSkillCode(requestParam.getSkillCode());
        boolean duplicated = agentSkillMapper.exists(Wrappers.lambdaQuery(AgentSkillDO.class)
                .eq(AgentSkillDO::getSkillCode, skillCode));
        if (duplicated) {
            throw new ClientException("Skill code already exists: " + skillCode);
        }

        AgentSkillDO record = AgentSkillDO.builder()
                .skillCode(skillCode)
                .name(requireName(requestParam.getName()))
                .description(requireDescription(requestParam.getDescription()))
                .content(requireContent(requestParam.getContent()))
                .toolIds(validateToolIds(requestParam.getToolIds()))
                .sortOrder(requestParam.getSortOrder() == null ? 0 : requestParam.getSortOrder())
                .enabled(Boolean.FALSE.equals(requestParam.getEnabled()) ? 0 : 1)
                .build();
        agentSkillMapper.insert(record);
        cacheManager.clearCache();
        bizChangeLogContext.put(record.getId(), null, record);
        return record.getId();
    }

    @Override
    @LogRecord(
            success = "更新技能：{{#id}}",
            fail = "更新技能失败：{{#_errorMsg}}",
            type = BizChangeBizType.AGENT_SKILL,
            subType = BizChangeOperationType.UPDATE,
            bizNo = "{{#id}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public void update(String id, AgentSkillSaveRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("Request cannot be empty"));
        AgentSkillDO record = loadById(id);
        AgentSkillDO before = BeanUtil.copyProperties(record, AgentSkillDO.class);

        // The skill id is not updated with the rest: other skills may refer to the body by name, so renaming it is a different skill
        if (requestParam.getName() != null) {
            record.setName(requireName(requestParam.getName()));
        }
        if (requestParam.getDescription() != null) {
            record.setDescription(requireDescription(requestParam.getDescription()));
        }
        if (requestParam.getContent() != null) {
            record.setContent(requireContent(requestParam.getContent()));
        }
        if (requestParam.getToolIds() != null) {
            record.setToolIds(validateToolIds(requestParam.getToolIds()));
        }
        if (requestParam.getSortOrder() != null) {
            record.setSortOrder(requestParam.getSortOrder());
        }
        if (requestParam.getEnabled() != null) {
            record.setEnabled(requestParam.getEnabled() ? 1 : 0);
        }

        agentSkillMapper.updateById(record);
        cacheManager.clearCache();
        bizChangeLogContext.put(id, before, agentSkillMapper.selectById(id));
    }

    @Override
    @LogRecord(
            success = "删除技能：{{#id}}",
            fail = "删除技能失败：{{#_errorMsg}}",
            type = BizChangeBizType.AGENT_SKILL,
            subType = BizChangeOperationType.DELETE,
            bizNo = "{{#id}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public void delete(String id) {
        AgentSkillDO record = loadById(id);
        agentSkillMapper.deleteById(record.getId());
        cacheManager.clearCache();
        bizChangeLogContext.put(id, record, null);
    }

    @Override
    @LogRecord(
            success = "{{#enabled ? '启用' : '停用'}}技能：{{#id}}",
            fail = "切换技能状态失败：{{#_errorMsg}}",
            type = BizChangeBizType.AGENT_SKILL,
            subType = "{{#enabled ? 'ENABLE' : 'DISABLE'}}",
            bizNo = "{{#id}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public void toggleEnabled(String id, boolean enabled) {
        AgentSkillDO record = loadById(id);
        AgentSkillDO before = BeanUtil.copyProperties(record, AgentSkillDO.class);
        record.setEnabled(enabled ? 1 : 0);
        agentSkillMapper.updateById(record);
        cacheManager.clearCache();
        bizChangeLogContext.put(id, before, agentSkillMapper.selectById(id));
    }

    @Override
    public List<AgentSkillToolOptionVO> listToolOptions() {
        Map<String, String> ownerBySkill = referencedToolOwners();
        List<AgentSkillToolOptionVO> options = new ArrayList<>();
        for (IntentNode node : intentNodeRegistry.listMcpToolNodes()) {
            String toolId = StrUtil.trimToNull(node.getMcpToolId());
            if (toolId == null) {
                continue;
            }
            options.add(AgentSkillToolOptionVO.builder()
                    .toolId(toolId)
                    .name(StrUtil.isBlank(node.getName()) ? toolId : node.getName())
                    .description(node.getDescription())
                    .referencedBySkillName(ownerBySkill.get(toolId))
                    .build());
        }
        return options;
    }

    /**
     * Tool to the name of a skill that references it. When several skills share it, keep the first one encountered
     */
    private Map<String, String> referencedToolOwners() {
        Map<String, String> owners = new HashMap<>();
        List<AgentSkillDO> skills = agentSkillMapper.selectList(
                Wrappers.lambdaQuery(AgentSkillDO.class).orderByAsc(AgentSkillDO::getSortOrder));
        for (AgentSkillDO skill : skills) {
            if (skill.getToolIds() == null) {
                continue;
            }
            skill.getToolIds().forEach(toolId -> owners.putIfAbsent(toolId, skill.getName()));
        }
        return owners;
    }

    /**
     * A tool must be an enabled MCP node on the intent tree: those are the tools the integrator actually opened to the Agent
     */
    private List<String> validateToolIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> available = new LinkedHashSet<>();
        intentNodeRegistry.listMcpToolNodes().forEach(node -> {
            String toolId = StrUtil.trimToNull(node.getMcpToolId());
            if (toolId != null) {
                available.add(toolId);
            }
        });

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String toolId : toolIds) {
            String trimmed = StrUtil.trimToNull(toolId);
            if (trimmed == null) {
                continue;
            }
            if (!available.contains(trimmed)) {
                throw new ClientException("Tool is not an available MCP node in the intent tree: " + trimmed);
            }
            selected.add(trimmed);
        }
        return List.copyOf(selected);
    }

    private String normalizeSkillCode(String skillCode) {
        String trimmed = StrUtil.trimToNull(skillCode);
        Assert.notBlank(trimmed, () -> new ClientException("Skill code cannot be empty"));
        if (!SKILL_CODE_PATTERN.matcher(trimmed).matches()) {
            throw new ClientException("Skill code must start with a lowercase letter and contain only letters, digits, and underscores, length 2-64");
        }
        return trimmed;
    }

    private String requireName(String name) {
        String trimmed = StrUtil.trimToNull(name);
        Assert.notBlank(trimmed, () -> new ClientException("Skill name cannot be empty"));
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ClientException("Skill name cannot exceed " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * When-to-use is the only signal the model has for loading the body. Leaving it blank means this skill is never selected
     */
    private String requireDescription(String description) {
        String trimmed = StrUtil.trimToNull(description);
        Assert.notBlank(trimmed, () -> new ClientException("Use case cannot be empty. The model uses it to decide when to apply this skill"));
        if (trimmed.length() > DESCRIPTION_MAX_LENGTH) {
            throw new ClientException("Use case cannot exceed " + DESCRIPTION_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private String requireContent(String content) {
        String trimmed = StrUtil.trimToNull(content);
        Assert.notBlank(trimmed, () -> new ClientException("Skill body cannot be empty"));
        return trimmed;
    }

    private AgentSkillDO loadById(String id) {
        AgentSkillDO record = agentSkillMapper.selectById(id);
        Assert.notNull(record, () -> new ClientException("Skill does not exist"));
        return record;
    }

    private AgentSkillVO toVO(AgentSkillDO record, boolean withContent) {
        return AgentSkillVO.builder()
                .id(record.getId())
                .skillCode(record.getSkillCode())
                .name(record.getName())
                .description(record.getDescription())
                .content(withContent ? record.getContent() : null)
                .toolIds(record.getToolIds() == null ? List.of() : record.getToolIds())
                .sortOrder(record.getSortOrder())
                .enabled(record.getEnabled() != null && record.getEnabled() == 1)
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }
}
