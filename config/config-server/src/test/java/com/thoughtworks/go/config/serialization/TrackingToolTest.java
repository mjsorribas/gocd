/*
 * Copyright Thoughtworks, Inc.
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
package com.thoughtworks.go.config.serialization;

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.util.ConfigElementImplementationRegistryMother;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.thoughtworks.go.helper.ConfigFileFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

public class TrackingToolTest {
    private MagicalGoConfigXmlLoader loader;
    private MagicalGoConfigXmlWriter writer;
    private ConfigCache configCache = new ConfigCache();

    @BeforeEach
    public void setUp() {
        loader = new MagicalGoConfigXmlLoader(configCache, ConfigElementImplementationRegistryMother.withNoPlugins());
        writer = new MagicalGoConfigXmlWriter(configCache, ConfigElementImplementationRegistryMother.withNoPlugins());
    }

    @Test
    public void shouldLoadTrackingTool() throws Exception {
        CruiseConfig cruiseConfig = loader.loadConfigHolder(CONFIG_WITH_TRACKINGTOOL).config;
        PipelineConfig pipelineConfig = cruiseConfig.pipelineConfigByName(new CaseInsensitiveString("pipeline1"));
        TrackingTool trackingTool = pipelineConfig.trackingTool();

        assertThat(trackingTool.getLink()).isEqualTo("http://mingle05/projects/cce/cards/${ID}");
        assertThat(trackingTool.getRegex()).isEqualTo("(evo-\\d+)");
    }

    @Test
    public void shouldWriteTrackingToolToConfig() throws Exception {
        CruiseConfig cruiseConfig = loader.loadConfigHolder(CONFIG_WITH_TRACKINGTOOL).config;
        PipelineConfig pipelineConfig = cruiseConfig.pipelineConfigByName(new CaseInsensitiveString("pipeline1"));

        assertThat(writer.toXmlPartial(pipelineConfig)).isEqualTo(PIPELINE_WITH_TRACKINGTOOL);
    }

    @Test
    public void shouldNotReturnNullWhenTrackingToolIsNotConfigured() throws Exception {
        CruiseConfig cruiseConfig = loader.loadConfigHolder(ONE_PIPELINE).config;
        PipelineConfig pipelineConfig = cruiseConfig.pipelineConfigByName(new CaseInsensitiveString("pipeline1"));
        TrackingTool trackingTool = pipelineConfig.trackingTool();

        assertThat(trackingTool).isNotNull();
        assertThat(trackingTool.getLink()).isEqualTo("");
        assertThat(trackingTool.getRegex()).isEqualTo("");
    }
}
