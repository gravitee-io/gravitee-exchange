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
package io.gravitee.exchange.controller.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.gravitee.exchange.api.batch.Batch;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelCandidateHealthcheckEvent;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelEvictedEvent;
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.Member;
import io.gravitee.node.api.cluster.messaging.Queue;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.gravitee.node.management.http.endpoint.ManagementEndpointManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultExchangeControllerTest {

    @Mock
    private ClusterManager clusterManager;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private ManagementEndpointManager managementEndpointManager;

    @Mock
    private Cache<String, Batch> cache;

    @Mock
    private Topic<PrimaryChannelEvictedEvent> topic;

    @Mock
    private Queue<PrimaryChannelCandidateHealthcheckEvent.Response> queue;

    private MockEnvironment environment;
    private DefaultExchangeController cut;

    @BeforeEach
    public void beforeEach() {
        lenient().when(clusterManager.<PrimaryChannelEvictedEvent>topic(anyString())).thenReturn(topic);
        lenient().when(clusterManager.<PrimaryChannelCandidateHealthcheckEvent.Response>queue(anyString())).thenReturn(queue);
        Member member = mock(Member.class);
        lenient().when(member.primary()).thenReturn(true);
        lenient().when(clusterManager.self()).thenReturn(member);

        lenient().when(cacheManager.<String, Batch>getOrCreateCache(anyString(), any(CacheConfiguration.class))).thenReturn(cache);
        environment = new MockEnvironment();
        cut =
            new DefaultExchangeController(new IdentifyConfiguration(environment), clusterManager, cacheManager, managementEndpointManager);
    }

    @Test
    void should_not_register_any_endpoints_management_when_disabled() throws Exception {
        environment.withProperty("exchange.controller.management.enabled", "false");
        cut =
            new DefaultExchangeController(new IdentifyConfiguration(environment), clusterManager, cacheManager, managementEndpointManager);
        cut.start();
        verify(managementEndpointManager, never()).register(any());
    }

    @Test
    void should_not_register_any_endpoints_management_when_manager_is_null() {
        cut = new DefaultExchangeController(new IdentifyConfiguration(environment), clusterManager, cacheManager);
        assertDoesNotThrow(() -> cut.start());
    }

    @Test
    void should_register_endpoints_management() throws Exception {
        cut.start();
        verify(managementEndpointManager, times(8)).register(any());
    }

    @Test
    void should_register_only_channels_endpoints_management_when_batch_feature_is_disabled() throws Exception {
        environment.withProperty("exchange.controller.batch.enabled", "false");
        cut =
            new DefaultExchangeController(new IdentifyConfiguration(environment), clusterManager, cacheManager, managementEndpointManager);
        cut.start();
        verify(managementEndpointManager, times(5)).register(any());
    }

    @Test
    void should_unregister_endpoints_management() throws Exception {
        cut.start();
        verify(managementEndpointManager, times(8)).register(any());
        cut.stop();
        verify(managementEndpointManager, times(8)).unregister(any());
    }
}
