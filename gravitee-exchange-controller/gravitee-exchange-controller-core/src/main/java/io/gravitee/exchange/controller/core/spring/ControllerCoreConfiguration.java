/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
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
package io.gravitee.exchange.controller.core.spring;

import io.gravitee.exchange.controller.core.channel.ChannelManager;
import io.gravitee.exchange.controller.core.channel.LocalChannelRegistry;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager;
import io.gravitee.exchange.controller.core.cluster.ControllerClusterManager;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class ControllerCoreConfiguration {

    @Bean
    public LocalChannelRegistry channelRegistry() {
        return new LocalChannelRegistry();
    }

    @Bean
    public PrimaryChannelManager primaryChannelManager(final @Lazy ClusterManager clusterManager, final @Lazy CacheManager cacheManager) {
        return new PrimaryChannelManager(clusterManager, cacheManager);
    }

    @Bean
    public ChannelManager channelManager(
            final LocalChannelRegistry localChannelRegistry,
            final PrimaryChannelManager primaryChannelManager,
            final @Lazy ClusterManager clusterManager
    ) {
        return new ChannelManager(localChannelRegistry, primaryChannelManager, clusterManager);
    }

    @Bean
    public ControllerClusterManager controllerClusterManager(
            final @Lazy ClusterManager clusterManager,
            final ChannelManager channelManager
    ) {
        return new ControllerClusterManager(clusterManager, channelManager);
    }

}
