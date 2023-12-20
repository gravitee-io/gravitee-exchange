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
package io.gravitee.exchange.controller.core.channel;

import io.gravitee.common.service.AbstractService;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommand;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommandPayload;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReplyPayload;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryCommandPayload;
import io.gravitee.exchange.api.command.primary.PrimaryReply;
import io.gravitee.exchange.api.controller.ControllerChannel;
import io.gravitee.exchange.controller.core.channel.exception.NoChannelFoundException;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelElectedEvent;
import io.gravitee.exchange.controller.core.channel.primary.PrimaryChannelManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.cluster.messaging.Topic;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class ChannelManager extends AbstractService<ChannelManager> {

    // TODO makes those constant configurable
    private static final int HEALTH_CHECK_DELAY = 30000;
    private static final TimeUnit HEALTH_CHECK_DELAY_UNIT = TimeUnit.MILLISECONDS;
    private final ChannelRegistry channelRegistry;
    private final PrimaryChannelManager primaryChannelManager;
    private final ClusterManager clusterManager;
    private Disposable healthCheckDisposable;
    private Topic<PrimaryChannelElectedEvent> primaryChannelElectedEventTopic;
    private String primaryChannelElectedSubscriptionId;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        primaryChannelManager.start();
        primaryChannelElectedEventTopic = clusterManager.topic(PrimaryChannelManager.PRIMARY_CHANNEL_EVENTS_ELECTED_TOPIC);
        primaryChannelElectedSubscriptionId =
            primaryChannelElectedEventTopic.addMessageListener(message -> handlePrimaryChannelElectedEvent(message.content()));
        healthCheckDisposable =
            Flowable
                .<Long, Long>generate(
                    () -> 0L,
                    (state, emitter) -> {
                        emitter.onNext(state);
                        return state + 1;
                    }
                )
                .delay(HEALTH_CHECK_DELAY, HEALTH_CHECK_DELAY_UNIT)
                .rebatchRequests(1)
                .doOnNext(aLong -> log.debug("Sending healthcheck command to all registered channels"))
                .flatMapCompletable(interval -> sendHealthCheckCommand())
                .onErrorComplete()
                .subscribe();
    }

    private void handlePrimaryChannelElectedEvent(final PrimaryChannelElectedEvent event) {
        Completable
            .defer(() -> {
                String channelId = event.channelId();
                String targetId = event.targetId();
                log.debug("Handling primary channel elected event for channel [{}] on target [{}].", channelId, targetId);

                return Maybe
                    .fromOptional(channelRegistry.getById(channelId))
                    .switchIfEmpty(
                        Maybe.fromRunnable(() ->
                            log.debug(
                                "Primary elected channel [{}] on target [{}] was not found from the local registry, ignore it.",
                                channelId,
                                targetId
                            )
                        )
                    )
                    .flatMapCompletable(channel -> sendPrimaryCommand(channel, true))
                    .andThen(
                        Flowable
                            .fromIterable(channelRegistry.getAllByTargetId(targetId))
                            .flatMapCompletable(controllerChannel -> sendPrimaryCommand(controllerChannel, false))
                    );
            })
            .subscribe(
                () -> log.debug("Primary channel elected event properly handled"),
                throwable -> log.error("Unable to send primary commands to local registered channels", throwable)
            );
    }

    private static Completable sendPrimaryCommand(final ControllerChannel channel, final boolean isPrimary) {
        String channelId = channel.id();
        String targetId = channel.targetId();
        log.debug("Sending primary command to channel [{}] on target [{}] with primary [{}]", channelId, targetId, isPrimary);
        return channel
            .<PrimaryCommand, PrimaryReply>send(new PrimaryCommand(new PrimaryCommandPayload(isPrimary)))
            .doOnSuccess(primaryReply -> {
                log.debug("Primary command successfully sent to channel [{}]", channelId);
                if (primaryReply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                    log.debug("Channel [{}] successfully replied from primary command", channelId);
                } else if (primaryReply.getCommandStatus() == CommandStatus.ERROR) {
                    log.warn("Channel [{}] replied in error from primary command", channelId);
                }
            })
            .doOnError(throwable -> log.warn("Unable to send primary command to channel [{}]", channelId, throwable))
            .ignoreElement();
    }

    private Completable sendHealthCheckCommand() {
        return Flowable
            .fromIterable(channelRegistry.getAll())
            .filter(ControllerChannel::isActive)
            .flatMapCompletable(controllerChannel ->
                controllerChannel
                    .send(new HealthCheckCommand(new HealthCheckCommandPayload()))
                    .cast(HealthCheckReply.class)
                    .doOnSuccess(reply -> {
                        log.debug(
                            "Health check command successfully sent for channel [{}] on target [{}]",
                            controllerChannel.id(),
                            controllerChannel.targetId()
                        );
                        HealthCheckReplyPayload payload = reply.getPayload();
                        controllerChannel.enforceActiveStatus(payload.healthy());
                        primaryChannelManager.sendChannelEvent(controllerChannel, reply.getPayload().healthy());
                    })
                    .ignoreElement()
                    .onErrorResumeNext(throwable -> {
                        log.debug(
                            "Unable to send health check command for channel [{}] on target [{}]",
                            controllerChannel.id(),
                            controllerChannel.targetId()
                        );
                        controllerChannel.enforceActiveStatus(false);
                        primaryChannelManager.sendChannelEvent(controllerChannel, false);
                        return Completable.complete();
                    })
            );
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        primaryChannelManager.stop();
        if (healthCheckDisposable != null) {
            healthCheckDisposable.dispose();
        }
        if (primaryChannelElectedEventTopic != null && primaryChannelElectedSubscriptionId != null) {
            primaryChannelElectedEventTopic.removeMessageListener(primaryChannelElectedSubscriptionId);
        }
    }

    public ControllerChannel getChannelById(final String id) {
        return channelRegistry.getById(id).filter(ControllerChannel::isActive).orElse(null);
    }

    public ControllerChannel getOneChannelByTargetId(final String targetId) {
        return channelRegistry.getAllByTargetId(targetId).stream().filter(ControllerChannel::isActive).findFirst().orElse(null);
    }

    public Completable register(ControllerChannel controllerChannel) {
        return Completable
            .fromRunnable(() -> channelRegistry.add(controllerChannel))
            .andThen(controllerChannel.initialize())
            .doOnComplete(() -> {
                log.debug("Channel [{}] successfully register for target [{}]", controllerChannel.id(), controllerChannel.targetId());
                primaryChannelManager.sendChannelEvent(controllerChannel, true);
            })
            .onErrorResumeNext(throwable -> {
                log.warn(
                    "Unable to register channel [{}] for target [{}]",
                    controllerChannel.id(),
                    controllerChannel.targetId(),
                    throwable
                );
                return unregister(controllerChannel);
            });
    }

    public Completable unregister(ControllerChannel controllerChannel) {
        return Completable
            .fromRunnable(() -> channelRegistry.remove(controllerChannel))
            .andThen(controllerChannel.close())
            .doOnComplete(() -> {
                log.debug("Channel [{}] successfully unregister for target [{}]", controllerChannel.id(), controllerChannel.targetId());
                primaryChannelManager.sendChannelEvent(controllerChannel, false);
            })
            .doOnError(throwable ->
                log.warn(
                    "Unable to unregister channel [{}] for target [{}]",
                    controllerChannel.id(),
                    controllerChannel.targetId(),
                    throwable
                )
            );
    }

    public <C extends Command<?>, R extends Reply<?>> Single<R> send(C command, String targetId) {
        return Maybe
            .fromCallable(() -> getOneChannelByTargetId(targetId))
            .doOnComplete(() -> log.debug("No channel found for target [{}] to handle command [{}]", targetId, command.getType()))
            .switchIfEmpty(Single.error(new NoChannelFoundException()))
            .<R>flatMap(controllerChannel -> {
                log.debug("Sending command [{}] to channel [{}]", command, controllerChannel);
                return controllerChannel.send(command);
            })
            .doOnSuccess(reply -> log.debug("Command [{}] successfully sent", command.getId()))
            .doOnError(throwable -> log.warn("Unable to send command [{}]", command.getId(), throwable));
    }
}
