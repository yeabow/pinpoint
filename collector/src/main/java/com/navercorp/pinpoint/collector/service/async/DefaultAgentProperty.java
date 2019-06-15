/*
 * Copyright 2019 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.collector.service.async;

import com.navercorp.pinpoint.common.util.Assert;

import java.util.Map;

/**
 * @author Woonduk Kang(emeroad)
 */
public class DefaultAgentProperty implements AgentProperty {
    private final String agentId;
    private final long agentStartTime;
    private final Map<String, Object> properties;

    public DefaultAgentProperty(String agentId, long agentStartTime, Map<String, Object> properties) {
        this.agentId = Assert.requireNonNull(agentId, "agentId must not be null");
        this.agentStartTime = Assert.requireNonNull(agentStartTime, "agentStartTime must not be null");
        this.properties = Assert.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public long getStartTime() {
        return agentStartTime;
    }

    @Override
    public Object get(String key) {
        return properties.get(key);
    }

    @Override
    public String toString() {
        return "DefaultAgentProperty{" +
                "agentId='" + agentId + '\'' +
                ", agentStartTime=" + agentStartTime +
                ", properties=" + properties +
                '}';
    }
}
