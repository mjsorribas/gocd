/*
 * Copyright 2019 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.server.service;

import com.rits.cloning.Cloner;
import com.thoughtworks.go.config.*;
import com.thoughtworks.go.config.commands.EntityConfigUpdateCommand;
import com.thoughtworks.go.config.exceptions.EntityType;
import com.thoughtworks.go.config.exceptions.GoConfigInvalidException;
import com.thoughtworks.go.config.exceptions.RecordNotFoundException;
import com.thoughtworks.go.config.remote.ConfigRepoConfig;
import com.thoughtworks.go.config.update.AddEnvironmentCommand;
import com.thoughtworks.go.config.update.DeleteEnvironmentCommand;
import com.thoughtworks.go.config.update.PatchEnvironmentCommand;
import com.thoughtworks.go.config.update.UpdateEnvironmentCommand;
import com.thoughtworks.go.domain.*;
import com.thoughtworks.go.i18n.LocalizedMessage;
import com.thoughtworks.go.listener.ConfigChangedListener;
import com.thoughtworks.go.listener.EntityConfigChangedListener;
import com.thoughtworks.go.presentation.environment.EnvironmentPipelineModel;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.service.result.HttpLocalizedOperationResult;
import com.thoughtworks.go.server.transaction.TransactionTemplate;
import com.thoughtworks.go.server.ui.EnvironmentViewModel;
import com.thoughtworks.go.util.command.EnvironmentVariableContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.thoughtworks.go.i18n.LocalizedMessage.entityConfigValidationFailed;

/**
 * @understands grouping of agents and pipelines within an environment
 */
@Service
public class EnvironmentConfigService implements ConfigChangedListener {
    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentConfigService.class.getName());
    public final GoConfigService goConfigService;
    private final SecurityService securityService;
    private EntityHashingService entityHashingService;
    private AgentConfigService agentConfigService;

    private EnvironmentsConfig environments;
    private EnvironmentPipelineMatchers matchers;
    private static final Cloner cloner = new Cloner();

    private final TransactionTemplate transactionTemplate;

    @Autowired
    public EnvironmentConfigService(GoConfigService goConfigService, SecurityService securityService, EntityHashingService entityHashingService, AgentConfigService agentConfigService, TransactionTemplate transactionTemplate) {
        this.goConfigService = goConfigService;
        this.securityService = securityService;
        this.entityHashingService = entityHashingService;
        this.agentConfigService = agentConfigService;
        this.transactionTemplate = transactionTemplate;
    }

    public void initialize() {
        goConfigService.register(this);

        goConfigService.register(new EntityConfigChangedListener<EnvironmentConfig>() {
            @Override
            public void onEntityConfigChange(EnvironmentConfig entity) {
                syncEnvironmentsFromConfig(goConfigService.getEnvironments());
            }
        });

        goConfigService.register(new EntityConfigChangedListener<ConfigRepoConfig>() {
            @Override
            public void onEntityConfigChange(ConfigRepoConfig entity) {
                if(!goConfigService.getCurrentConfig().getConfigRepos().hasConfigRepo(entity.getId())) {
                    sync(goConfigService.getEnvironments());
                }
            }
        });
    }

    public void syncEnvironmentsFromConfig(EnvironmentsConfig environments){
        this.environments = environments;
        matchers = this.environments.matchers();
    }

    public void syncAssociatedAgentsFromDB(){
        agentConfigService
                .agents()
                .forEach(agent -> {
                    List<String> associatedEnvNames = agent.getEnvironmentsAsList();
                    addAgentToAssociatedEnvs(agent.getUuid(), associatedEnvNames);
                    removeAgentFromDissociatedEnvs(associatedEnvNames, agent.getUuid());
                });
        matchers = this.environments.matchers();
    }

    private void removeAgentFromDissociatedEnvs(List<String> associatedEnvNameList, String agentUuid) {
        this.environments
            .stream()
            .filter(env -> !(associatedEnvNameList.contains(env.name().toString())))
            .forEach(env -> {
                if(env.hasAgent(agentUuid)){
                    env.removeAgent(agentUuid);
                }
            });
    }

    private void addAgentToAssociatedEnvs(String agentUuid, List<String> associatedEnvNames) {
        associatedEnvNames.forEach(associatedEnvName -> {
            EnvironmentConfig env = this.environments.find(new CaseInsensitiveString(associatedEnvName));
            if (env != null && !env.hasAgent(agentUuid)) {
                env.addAgent(agentUuid);
            }
        });
    }

    public void sync(EnvironmentsConfig environments) {
        this.environments = environments;
        agentConfigService.agents().forEach(agentConfig -> {
            String agentEnvironments = agentConfig.getEnvironments();
            if (agentEnvironments != null) {
                Arrays.stream(agentEnvironments.split(",")).forEach(env -> {
                    EnvironmentConfig environmentConfig = this.environments.find(new CaseInsensitiveString(env));
                    if (environmentConfig != null && !environmentConfig.hasAgent(agentConfig.getUuid())) {
                        environmentConfig.addAgent(agentConfig.getUuid());
                    }
                });
            }

        });
        matchers = this.environments.matchers();
    }

    @Override
    public void onConfigChange(CruiseConfig newCruiseConfig) {
        sync(newCruiseConfig.getEnvironments());
    }

    public List<JobPlan> filterJobsByAgent(List<JobPlan> jobPlans, String agentUuid) {
        ArrayList<JobPlan> plans = new ArrayList<>();
        for (JobPlan jobPlan : jobPlans) {
            if (matchers.match(jobPlan.getPipelineName(), agentUuid)) {
                plans.add(jobPlan);
            }
        }
        return plans;
    }

    public String envForPipeline(String pipelineName) {
        for (EnvironmentPipelineMatcher matcher : matchers) {
            if (matcher.hasPipeline(pipelineName)) {
                return CaseInsensitiveString.str(matcher.name());
            }
        }
        return null;
    }

    public EnvironmentConfig environmentForPipeline(String pipelineName) {
        return environments.findEnvironmentForPipeline(new CaseInsensitiveString(pipelineName));
    }

    public Agents agentsForPipeline(final CaseInsensitiveString pipelineName) {
        Agents configs = new Agents();
        if (environments.isPipelineAssociatedWithAnyEnvironment(pipelineName)) {
            EnvironmentConfig forPipeline = environments.findEnvironmentForPipeline(pipelineName);
            for (EnvironmentAgentConfig environmentAgentConfig : forPipeline.getAgents()) {
                configs.add(agentConfigService.agentByUuid(environmentAgentConfig.getUuid()));
            }

        } else {
            for (AgentConfig agentConfig : agentConfigService.agents()) {
                if (!environments.isAgentUnderEnvironment(agentConfig.getUuid())) {
                    configs.add(agentConfig);
                }
            }
        }
        return configs;
    }

    public List<CaseInsensitiveString> pipelinesFor(final CaseInsensitiveString environmentName) {
        return environments.named(environmentName).getPipelineNames();
    }

    public List<CaseInsensitiveString> environmentNames() {
        return environments.names();
    }

    public Set<EnvironmentConfig> getEnvironments() {
        Set<EnvironmentConfig> environmentConfigs = new HashSet<>();
        environmentConfigs.addAll(environments);
        return environmentConfigs;
    }

    public Set<String> environmentsFor(String uuid) {
        return environments.environmentsForAgent(uuid);
    }

    public Set<EnvironmentConfig> environmentConfigsFor(String agentUuid) {
        return environments.environmentConfigsForAgent(agentUuid);
    }

    public EnvironmentConfig named(String environmentName) {
        return environments.named(new CaseInsensitiveString(environmentName));
    }

    public EnvironmentConfig getEnvironmentConfig(String environmentName) {
        return environments.named(new CaseInsensitiveString(environmentName));
    }

    public EnvironmentConfig getEnvironmentForEdit(String environmentName) {
        return cloner.deepClone(goConfigService.getConfigForEditing().getEnvironments().find(new CaseInsensitiveString(environmentName)));
    }

    public List<EnvironmentConfig> getAllLocalEnvironments() {
        return environmentNames().stream().map(environmentName -> EnvironmentConfigService.this.getEnvironmentForEdit(environmentName.toString())).collect(Collectors.toList());
    }

    public List<EnvironmentConfig> getAllMergedEnvironments() {
        return environmentNames().stream().map(environmentName -> EnvironmentConfigService.this.getMergedEnvironmentforDisplay(environmentName.toString(), new HttpLocalizedOperationResult()).getConfigElement()).collect(Collectors.toList());
    }

    public List<EnvironmentViewModel> listAllMergedEnvironments() {
        ArrayList<EnvironmentViewModel> environmentViewModels = new ArrayList<>();
        List<EnvironmentConfig> allMergedEnvironments = getAllMergedEnvironments();
        for (EnvironmentConfig environmentConfig : allMergedEnvironments) {
            environmentViewModels.add(new EnvironmentViewModel(environmentConfig));
        }
        return environmentViewModels;
    }

    public ConfigElementForEdit<EnvironmentConfig> getMergedEnvironmentforDisplay(String environmentName, HttpLocalizedOperationResult result) {
        ConfigElementForEdit<EnvironmentConfig> edit = null;
        try {
            CruiseConfig config = goConfigService.getMergedConfigForEditing();
            EnvironmentConfig env = environments.named(new CaseInsensitiveString(environmentName));
            edit = new ConfigElementForEdit<>(cloner.deepClone(env), config.getMd5());
        } catch (RecordNotFoundException e) {
            result.badRequest(EntityType.Environment.notFoundMessage(environmentName));
        }
        return edit;
    }

    public void createEnvironment(final BasicEnvironmentConfig environmentConfig, final Username user, final HttpLocalizedOperationResult result) {
        String actionFailed = "Failed to add environment '" + environmentConfig.name() + "'.";
        AddEnvironmentCommand addEnvironmentCommand = new AddEnvironmentCommand(goConfigService, environmentConfig, user, actionFailed, result, agentConfigService);
        update(addEnvironmentCommand, environmentConfig, user, result, actionFailed);
    }

    public List<EnvironmentPipelineModel> getAllLocalPipelinesForUser(Username user) {
        List<PipelineConfig> pipelineConfigs = goConfigService.getAllLocalPipelineConfigs();
        return getAllPipelinesForUser(user, pipelineConfigs);
    }

    public List<EnvironmentPipelineModel> getAllRemotePipelinesForUserInEnvironment(Username user, EnvironmentConfig environment) {
        List<EnvironmentPipelineModel> pipelines = new ArrayList<>();
        for (EnvironmentPipelineConfig pipelineConfig : environment.getRemotePipelines()) {
            String pipelineName = CaseInsensitiveString.str(pipelineConfig.getName());
            if (securityService.hasViewPermissionForPipeline(user, pipelineName)) {
                pipelines.add(new EnvironmentPipelineModel(pipelineName, CaseInsensitiveString.str(environment.name())));
            }
        }
        Collections.sort(pipelines);
        return pipelines;
    }

    private List<EnvironmentPipelineModel> getAllPipelinesForUser(Username user, List<PipelineConfig> pipelineConfigs) {
        List<EnvironmentPipelineModel> pipelines = new ArrayList<>();
        for (PipelineConfig pipelineConfig : pipelineConfigs) {
            String pipelineName = CaseInsensitiveString.str(pipelineConfig.name());
            if (securityService.hasViewPermissionForPipeline(user, pipelineName)) {
                EnvironmentConfig environment = environments.findEnvironmentForPipeline(new CaseInsensitiveString(pipelineName));
                if (environment != null) {
                    pipelines.add(new EnvironmentPipelineModel(pipelineName, CaseInsensitiveString.str(environment.name())));
                } else {
                    pipelines.add(new EnvironmentPipelineModel(pipelineName));
                }
            }
        }
        Collections.sort(pipelines);
        return pipelines;
    }

    public void updateEnvironment(final String oldEnvName, final EnvironmentConfig env, final Username username, String md5, final HttpLocalizedOperationResult result) {
        String failureMsg = "Failed to update environment '" + oldEnvName + "'.";
        UpdateEnvironmentCommand updateEnvCmd = new UpdateEnvironmentCommand(goConfigService, oldEnvName, env, username, failureMsg, md5, entityHashingService, result, agentConfigService);
        update(updateEnvCmd, env, username, result, failureMsg);

        if (result.isSuccessful()) {
            result.setMessage("Updated environment '" + oldEnvName + "'.");
        }
    }

    public void patchEnvironment(final EnvironmentConfig environmentConfig, List<String> pipelinesToAdd, List<String> pipelinesToRemove, List<String> agentsToAdd, List<String> agentsToRemove, List<EnvironmentVariableConfig> envVarsToAdd, List<String> envVarsToRemove, final Username username, final HttpLocalizedOperationResult result) {
        String actionFailed = "Failed to update environment '" + environmentConfig.name() + "'.";
        PatchEnvironmentCommand patchEnvironmentCommand = new PatchEnvironmentCommand(goConfigService, environmentConfig, pipelinesToAdd, pipelinesToRemove, agentsToAdd, agentsToRemove, envVarsToAdd, envVarsToRemove, username, actionFailed, result, agentConfigService);
        update(patchEnvironmentCommand, environmentConfig, username, result, actionFailed);
        if (result.isSuccessful()) {
            result.setMessage("Updated environment '" + environmentConfig.name() + "'.");
        }
    }

    public void deleteEnvironment(final EnvironmentConfig environmentConfig, final Username username, final HttpLocalizedOperationResult result) {
        String environmentName = environmentConfig.name().toString();
        String actionFailed = "Failed to delete environment '" + environmentConfig.name() + "'.";
        DeleteEnvironmentCommand deleteEnvironmentCommand = new DeleteEnvironmentCommand(goConfigService, environmentConfig, username, actionFailed, result, agentConfigService);
        update(deleteEnvironmentCommand, environmentConfig, username, result, actionFailed);
        if (result.isSuccessful()) {
            result.setMessage(EntityType.Environment.deleteSuccessful(environmentName));
        }
    }

    private void update(EntityConfigUpdateCommand updateEnvCmd, EnvironmentConfig config, Username currentUser, HttpLocalizedOperationResult result, String actionFailed) {
        try {
            goConfigService.updateConfig(updateEnvCmd, currentUser);
        } catch (Exception e) {
            if ((e instanceof GoConfigInvalidException) && !result.hasMessage()) {
                result.unprocessableEntity(entityConfigValidationFailed(config.getClass().getAnnotation(ConfigTag.class).value(), config.name(), e.getMessage()));
            } else if (!result.hasMessage()) {
                result.badRequest(LocalizedMessage.composite(actionFailed, e.getMessage()));
            }
        }
    }
}
