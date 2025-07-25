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
package io.gravitee.exchange.api.websocket.channel;

import static io.gravitee.exchange.api.command.CommandStatus.ERROR;

import io.gravitee.exchange.api.channel.Channel;
import io.gravitee.exchange.api.channel.exception.ChannelClosedException;
import io.gravitee.exchange.api.channel.exception.ChannelInactiveException;
import io.gravitee.exchange.api.channel.exception.ChannelInitializationException;
import io.gravitee.exchange.api.channel.exception.ChannelNoReplyException;
import io.gravitee.exchange.api.channel.exception.ChannelReplyException;
import io.gravitee.exchange.api.channel.exception.ChannelTimeoutException;
import io.gravitee.exchange.api.channel.exception.ChannelUnknownCommandException;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Payload;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommandPayload;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.command.hello.HelloReply;
import io.gravitee.exchange.api.command.hello.HelloReplyPayload;
import io.gravitee.exchange.api.command.noreply.NoReply;
import io.gravitee.exchange.api.command.unknown.UnknownCommandHandler;
import io.gravitee.exchange.api.command.unknown.UnknownReply;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolExchange;
import io.gravitee.exchange.api.websocket.protocol.legacy.ignored.IgnoredReply;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableEmitter;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.WebSocketBase;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public abstract class AbstractWebSocketChannel implements Channel {

    private static final int PING_DELAY = 5_000;
    protected final String id = UUID.randomUUID().toString();
    protected final Map<String, CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers = new ConcurrentHashMap<>();
    protected final Map<String, CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters =
        new ConcurrentHashMap<>();
    protected final Map<String, ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters = new ConcurrentHashMap<>();
    protected final Vertx vertx;
    protected final WebSocketBase webSocket;
    protected final ProtocolAdapter protocolAdapter;
    protected String targetId;
    protected final Map<String, SingleEmitter<? extends Reply<?>>> resultEmitters = new ConcurrentHashMap<>();
    protected boolean active;
    protected AtomicInteger inflightCommandCount = new AtomicInteger(0);
    private long pingTaskId = -1;

    protected AbstractWebSocketChannel(
        final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers,
        final List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters,
        final List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters,
        final Vertx vertx,
        final WebSocketBase webSocket,
        final ProtocolAdapter protocolAdapter
    ) {
        this.addCommandHandlers(commandHandlers);
        this.addCommandHandlers(List.of(new UnknownCommandHandler()));
        this.addCommandHandlers(protocolAdapter.commandHandlers());
        this.addCommandAdapters(commandAdapters);
        this.addCommandAdapters(protocolAdapter.commandAdapters());
        this.addReplyAdapters(replyAdapters);
        this.addReplyAdapters(protocolAdapter.replyAdapters());
        this.vertx = vertx;
        this.webSocket = webSocket;
        this.protocolAdapter = protocolAdapter;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String targetId() {
        return targetId;
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public boolean hasPendingCommands() {
        return !resultEmitters.isEmpty() || inflightCommandCount.get() > 0;
    }

    @Override
    public Completable initialize() {
        return Completable
            .create(emitter -> {
                webSocket.closeHandler(v -> {
                    log.warn("Channel '{}' for target '{}' is closing", id, targetId);
                    active = false;
                    cleanChannel();
                });

                webSocket.pongHandler(buffer -> log.trace("Receiving pong frame from channel '{}' for target '{}'", id, targetId));

                webSocket.textMessageHandler(buffer -> webSocket.close((short) 1003, "Unsupported text frame").subscribe());

                webSocket.binaryMessageHandler(buffer -> {
                    if (buffer.length() > 0) {
                        ProtocolExchange websocketExchange = protocolAdapter.read(buffer);

                        try {
                            if (ProtocolExchange.Type.COMMAND == websocketExchange.type()) {
                                receiveCommand(emitter, websocketExchange.asCommand());
                            } else if (ProtocolExchange.Type.REPLY == websocketExchange.type()) {
                                receiveReply(websocketExchange.asReply());
                            } else {
                                webSocket.close((short) 1002, "Exchange message unknown").subscribe();
                            }
                        } catch (Exception e) {
                            log.warn(
                                String.format(
                                    "An error occurred when trying to decode incoming websocket exchange [%s] for target '%s'. Closing Socket.",
                                    websocketExchange,
                                    targetId
                                ),
                                e
                            );
                            webSocket.close((short) 1011, "Unexpected error while handling incoming websocket exchange").subscribe();
                        }
                    }
                });

                if (!expectHelloCommand()) {
                    this.active = true;
                    emitter.onComplete();
                }
            })
            .doOnComplete(() -> log.debug("Channel '{}' for target '{}' has been successfully initialized", id, targetId))
            .doOnError(throwable -> log.error("Unable to initialize channel '{}'", id));
    }

    private <C extends Command<?>> void receiveCommand(final CompletableEmitter emitter, final C command) {
        if (command == null) {
            webSocket.close((short) 1002, "Unrecognized incoming exchange").subscribe();
            emitter.onError(new ChannelUnknownCommandException("Unrecognized incoming exchange"));
            return;
        }

        log.trace("Handling received command '{}' of type '{}' for target '{}'", command.getId(), command.getType(), targetId);
        Single<? extends Command<?>> commandObs;
        CommandAdapter<Command<?>, Command<?>, Reply<?>> commandAdapter =
            (CommandAdapter<Command<?>, Command<?>, Reply<?>>) commandAdapters.get(command.getType());
        if (commandAdapter != null) {
            commandObs = commandAdapter.adapt(targetId, command);
        } else {
            commandObs = Single.just(command);
        }
        commandObs
            .flatMapCompletable(adaptedCommand -> {
                CommandHandler<Command<?>, Reply<?>> commandHandler = (CommandHandler<Command<?>, Reply<?>>) commandHandlers.get(
                    adaptedCommand.getType()
                );
                if (expectHelloCommand() && !active && !Objects.equals(adaptedCommand.getType(), HelloCommand.COMMAND_TYPE)) {
                    webSocket.close((short) 1002, "Hello Command is first expected to initialize the exchange channel").subscribe();
                    emitter.onError(new ChannelInitializationException("Hello Command is first expected to initialize the channel"));
                } else if (Objects.equals(adaptedCommand.getType(), HelloCommand.COMMAND_TYPE)) {
                    return handleHelloCommand(emitter, adaptedCommand, commandHandler);
                } else if (Objects.equals(adaptedCommand.getType(), GoodByeCommand.COMMAND_TYPE)) {
                    return handleGoodByeCommand(adaptedCommand, commandHandler);
                } else if (commandHandler != null) {
                    return handleCommandAsync(adaptedCommand, commandHandler);
                } else {
                    log.info("No handler found for command type '{}' for target '{}'. Ignoring", adaptedCommand.getType(), targetId);
                    return writeReply(
                        new NoReply(
                            adaptedCommand.getId(),
                            "No handler found for command type '%s'. Ignoring".formatted(adaptedCommand.getType())
                        )
                    );
                }
                return Completable.complete();
            })
            .onErrorResumeNext(throwable -> {
                log.warn(
                    "Unexpected internal error occurred when handling command '%s' of type '%s' for target '%s'".formatted(
                            command.getId(),
                            command.getType(),
                            targetId
                        ),
                    throwable
                );
                return writeReply(new NoReply(command.getId(), "Unexpected internal error occurred"));
            })
            .subscribe();
    }

    protected abstract boolean expectHelloCommand();

    private void receiveReply(final Reply<?> reply) {
        SingleEmitter<? extends Reply<?>> replyEmitter = resultEmitters.remove(reply.getCommandId());
        if (replyEmitter != null) {
            log.trace("Handling received reply '{}' of type '{}' for target '{}'", reply.getCommandId(), reply.getType(), targetId);
            Single<? extends Reply<?>> replyObs;
            ReplyAdapter<Reply<?>, Reply<?>> replyAdapter = (ReplyAdapter<Reply<?>, Reply<?>>) replyAdapters.get(reply.getType());
            if (replyAdapter != null) {
                replyObs = replyAdapter.adapt(targetId, reply);
            } else {
                replyObs = Single.just(reply);
            }
            replyObs
                .doOnSuccess(adaptedReply -> {
                    if (adaptedReply instanceof UnknownReply) {
                        replyEmitter.onError(new ChannelUnknownCommandException(adaptedReply.getErrorDetails()));
                    } else if (adaptedReply instanceof NoReply || adaptedReply instanceof IgnoredReply) {
                        replyEmitter.onError(new ChannelNoReplyException(adaptedReply.getErrorDetails()));
                    } else {
                        ((SingleEmitter<Reply<?>>) replyEmitter).onSuccess(adaptedReply);
                    }
                    if (adaptedReply.stopOnErrorStatus() && adaptedReply.getCommandStatus() == ERROR) {
                        webSocket.close().subscribe();
                    }
                })
                .doOnError(throwable -> {
                    log.warn(
                        "Unable to handle reply '{}' for command '{}' for target '{}'",
                        reply.getType(),
                        reply.getCommandId(),
                        targetId
                    );
                    replyEmitter.onError(new ChannelReplyException(throwable));
                })
                .subscribe();
        } else {
            log.debug(
                "No reply emitter for received reply '{}' of type '{}' for target '{}'. Ignoring",
                reply.getCommandId(),
                reply.getType(),
                targetId
            );
        }
    }

    @Override
    public Completable close() {
        return Completable.fromRunnable(() -> {
            webSocket.close((short) 1000).subscribe();
            this.cleanChannel();
        });
    }

    protected void cleanChannel() {
        this.active = false;
        this.resultEmitters.forEach((type, emitter) -> {
                if (!emitter.isDisposed()) {
                    emitter.onError(new ChannelClosedException());
                }
            });
        this.resultEmitters.clear();

        if (pingTaskId != -1) {
            this.vertx.cancelTimer(this.pingTaskId);
            this.pingTaskId = -1;
        }
        if (webSocket != null && !webSocket.isClosed()) {
            this.webSocket.close((short) 1011).subscribe();
        }
    }

    /**
     * Method call to handle initialize command type
     */
    protected Completable handleHelloCommand(
        final CompletableEmitter emitter,
        final Command<?> command,
        final CommandHandler<Command<?>, Reply<?>> commandHandler
    ) {
        if (commandHandler != null) {
            return handleCommand(command, commandHandler, false)
                .doOnSuccess(reply -> {
                    if (reply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                        Payload payload = reply.getPayload();
                        if (payload instanceof HelloReplyPayload helloReplyPayload) {
                            this.targetId = helloReplyPayload.getTargetId();
                            this.active = true;
                            startPingTask();
                            emitter.onComplete();
                        } else {
                            emitter.onError(new ChannelInitializationException("Unable to parse hello reply payload"));
                        }
                    }
                })
                .ignoreElement();
        } else {
            return Completable.fromRunnable(() -> {
                startPingTask();
                emitter.onComplete();
            });
        }
    }

    private void startPingTask() {
        this.pingTaskId =
            this.vertx.setPeriodic(
                    PING_DELAY,
                    timerId -> {
                        if (!this.webSocket.isClosed()) {
                            this.webSocket.writePing(Buffer.buffer()).subscribe();
                        }
                    }
                );
    }

    /**
     * Method call to handle custom command type
     */
    protected Completable handleGoodByeCommand(final Command<?> command, final CommandHandler<Command<?>, Reply<?>> commandHandler) {
        if (commandHandler != null) {
            return handleCommand(command, commandHandler, true)
                .doOnSuccess(reply -> {
                    if (reply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                        Payload payload = command.getPayload();
                        if (payload instanceof GoodByeCommandPayload goodByeCommandPayload && goodByeCommandPayload.isReconnect()) {
                            webSocket.close((short) 1013, "GoodBye Command with reconnection requested.").subscribe();
                        } else {
                            webSocket.close((short) 1000, "GoodBye Command without reconnection.").subscribe();
                        }
                    }
                })
                .doFinally(this::cleanChannel)
                .ignoreElement();
        } else {
            return Completable.fromRunnable(() -> {
                webSocket.close((short) 1013).subscribe();
                this.cleanChannel();
            });
        }
    }

    protected Completable handleCommandAsync(final Command<?> command, final CommandHandler<Command<?>, Reply<?>> commandHandler) {
        return handleCommand(command, commandHandler, false).ignoreElement();
    }

    protected Single<Reply<?>> handleCommand(
        final Command<?> command,
        final CommandHandler<Command<?>, Reply<?>> commandHandler,
        boolean dontReply
    ) {
        return Completable
            .fromRunnable(() -> inflightCommandCount.incrementAndGet())
            .andThen(Single.defer(() -> commandHandler.handle(command)))
            .flatMap(reply -> {
                if (!dontReply) {
                    return writeReply(reply).andThen(Single.just(reply));
                }
                return Single.just(reply);
            })
            .doOnError(throwable -> {
                log.warn("Unable to handle command '{}' with id '{}' for target '{}' ", command.getType(), command.getId(), targetId);
                webSocket.close((short) 1011, "Unexpected error").subscribe();
            })
            .doFinally(() -> inflightCommandCount.decrementAndGet());
    }

    @Override
    public <C extends Command<?>, R extends Reply<?>> Single<R> send(final C command) {
        return send(command, false);
    }

    protected Single<HelloReply> sendHelloCommand(final HelloCommand helloCommand) {
        return send(helloCommand, true);
    }

    protected <C extends Command<?>, R extends Reply<?>> Single<R> send(final C command, final boolean ignoreActiveStatus) {
        return Single
            .defer(() -> {
                if (!ignoreActiveStatus && !active) {
                    return Single.error(new ChannelInactiveException());
                }
                CommandAdapter<C, Command<?>, R> commandAdapter = (CommandAdapter<C, Command<?>, R>) commandAdapters.get(command.getType());
                if (commandAdapter != null) {
                    return commandAdapter.adapt(targetId, command);
                } else {
                    return Single.just(command);
                }
            })
            .flatMap(adaptedCommand ->
                Single
                    .<R>create(emitter -> {
                        resultEmitters.put(adaptedCommand.getId(), emitter);
                        writeCommand(adaptedCommand).doOnError(emitter::onError).onErrorComplete().subscribe();
                    })
                    .timeout(
                        adaptedCommand.getReplyTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        Single.error(() -> {
                            if (adaptedCommand.getReplyTimeoutMs() > 0) {
                                log.warn(
                                    "No reply received in time for command '{}' with id '{}' for target '{}'",
                                    adaptedCommand.getType(),
                                    adaptedCommand.getId(),
                                    targetId
                                );
                            }
                            throw new ChannelTimeoutException();
                        })
                    )
            )
            .onErrorResumeNext(throwable -> {
                CommandAdapter<C, Command<?>, R> commandAdapter = (CommandAdapter<C, Command<?>, R>) commandAdapters.get(command.getType());
                if (commandAdapter != null) {
                    return commandAdapter.onError(command, throwable);
                } else {
                    return Single.error(throwable);
                }
            })
            // Cleanup result emitters list if cancelled by the upstream.
            .doFinally(() -> resultEmitters.remove(command.getId()));
    }

    protected <C extends Command<?>> Completable writeCommand(C command) {
        ProtocolExchange protocolExchange = ProtocolExchange
            .builder()
            .type(ProtocolExchange.Type.COMMAND)
            .exchangeType(command.getType())
            .exchange(command)
            .build();
        return writeToSocket(command.getId(), protocolExchange);
    }

    protected <R extends Reply<?>> Completable writeReply(R reply) {
        return Single
            .defer(() -> {
                ReplyAdapter<R, Reply<?>> replyAdapter = (ReplyAdapter<R, Reply<?>>) replyAdapters.get(reply.getType());
                if (replyAdapter != null) {
                    return replyAdapter.adapt(targetId, reply);
                } else {
                    return Single.just(reply);
                }
            })
            .flatMapCompletable(adaptedReply -> {
                ProtocolExchange protocolExchange = ProtocolExchange
                    .builder()
                    .type(ProtocolExchange.Type.REPLY)
                    .exchangeType(adaptedReply.getType())
                    .exchange(adaptedReply)
                    .build();
                return writeToSocket(adaptedReply.getCommandId(), protocolExchange);
            });
    }

    private Completable writeToSocket(final String commandId, final ProtocolExchange websocketExchange) {
        if (!webSocket.isClosed()) {
            Buffer payload = protocolAdapter.write(websocketExchange);
            log.trace(
                "Writing exchange '{}' for '{}' for command id '{}' for target '{}' to websocket: {}",
                websocketExchange.type(),
                websocketExchange.exchangeType(),
                commandId,
                targetId,
                payload
            );
            return webSocket
                .writeBinaryMessage(payload)
                .doOnComplete(() ->
                    log.trace(
                        "Write exchange '{}' for '{}' and id '{}' for target '{}' to websocket successfully",
                        websocketExchange.type(),
                        websocketExchange.exchangeType(),
                        commandId,
                        targetId
                    )
                )
                .onErrorResumeNext(throwable -> {
                    log.error(
                        "An error occurred when trying to send exchange '{}' for '{}' and id '{}' for target '{}' ",
                        websocketExchange.type(),
                        websocketExchange.exchangeType(),
                        commandId,
                        targetId
                    );
                    return Completable.error(new Exception("Write to socket failed"));
                });
        } else {
            return Completable.error(new ChannelClosedException());
        }
    }

    @Override
    public void addCommandHandlers(final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers) {
        if (commandHandlers != null) {
            commandHandlers.forEach(commandHandler -> this.commandHandlers.putIfAbsent(commandHandler.supportType(), commandHandler));
        }
    }

    public void addCommandAdapters(
        final List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters
    ) {
        if (commandAdapters != null) {
            commandAdapters.forEach(commandAdapter -> this.commandAdapters.putIfAbsent(commandAdapter.supportType(), commandAdapter));
        }
    }

    public void addReplyAdapters(final List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters) {
        if (replyAdapters != null) {
            replyAdapters.forEach(replyAdapter -> this.replyAdapters.putIfAbsent(replyAdapter.supportType(), replyAdapter));
        }
    }
}
