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
import io.gravitee.exchange.api.channel.exception.ChannelTimeoutException;
import io.gravitee.exchange.api.channel.exception.ChannelUnknownCommandException;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Payload;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyHandler;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.command.hello.HelloReply;
import io.gravitee.exchange.api.command.hello.HelloReplyPayload;
import io.gravitee.exchange.api.command.noreply.NoReply;
import io.gravitee.exchange.api.command.unknown.UnknownCommandHandler;
import io.gravitee.exchange.api.command.unknown.UnknownReply;
import io.gravitee.exchange.api.websocket.protocol.ProtocolAdapter;
import io.gravitee.exchange.api.websocket.protocol.ProtocolExchange;
import io.gravitee.exchange.api.websocket.protocol.legacy.IgnoredReply;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableEmitter;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleSource;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.WebSocketBase;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
    protected final Map<String, ReplyHandler<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> replyHandlers = new ConcurrentHashMap<>();
    protected final Vertx vertx;
    protected final WebSocketBase webSocket;
    protected final ProtocolAdapter protocolAdapter;
    protected String targetId;
    protected final Map<String, SingleEmitter<? extends Reply<?>>> resultEmitters = new ConcurrentHashMap<>();
    protected boolean active;
    private long pingTaskId = -1;

    protected AbstractWebSocketChannel(
        final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers,
        final List<ReplyHandler<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> replyHandlers,
        final Vertx vertx,
        final WebSocketBase webSocket,
        final ProtocolAdapter protocolAdapter
    ) {
        this.addCommandHandlers(commandHandlers);
        this.addCommandHandlers(List.of(new UnknownCommandHandler()));
        this.addCommandHandlers(protocolAdapter.commandHandlers());
        this.addReplyHandlers(replyHandlers);
        this.addReplyHandlers(protocolAdapter.replyHandlers());
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
    public Completable initialize() {
        return Completable.create(emitter -> {
            webSocket.closeHandler(v -> {
                active = false;
                closeHandler();
                cleanChannel();
            });
            webSocket.pongHandler(buffer -> pongHandler());

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
                                "An error occurred when trying to decode incoming websocket exchange [%s]. Closing Socket.",
                                websocketExchange
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
        });
    }

    private void receiveCommand(final CompletableEmitter emitter, final Command<?> command) {
        if (command == null) {
            webSocket.close((short) 1002, "Unrecognized incoming exchange").subscribe();
            return;
        }
        CommandHandler<? extends Command<?>, ? extends Reply<?>> commandHandler = commandHandlers.get(command.getType());
        if (expectHelloCommand() && !active && !Objects.equals(command.getType(), HelloCommand.COMMAND_TYPE)) {
            webSocket.close((short) 1002, "Hello Command is first expected to initialize the exchange channel").subscribe();
            emitter.onError(new ChannelInitializationException("Hello Command is first expected to initialize the channel"));
        } else if (Objects.equals(command.getType(), HelloCommand.COMMAND_TYPE)) {
            handleHelloCommand(emitter, command, (CommandHandler<Command<?>, Reply<?>>) commandHandler);
        } else if (Objects.equals(command.getType(), GoodByeCommand.COMMAND_TYPE)) {
            handleGoodByeCommand(command, (CommandHandler<Command<?>, Reply<?>>) commandHandler);
        } else if (commandHandler != null) {
            handleCommandAsync(command, (CommandHandler<Command<?>, Reply<?>>) commandHandler);
        } else {
            log.info("No handler found for command type {}. Ignoring", command.getType());
            writeReply(new NoReply(command.getId(), "No handler found for command type %s. Ignoring".formatted(command.getType())));
        }
    }

    protected abstract boolean expectHelloCommand();

    private void receiveReply(final Reply<?> reply) {
        SingleEmitter<? extends Reply<?>> replyEmitter = resultEmitters.remove(reply.getCommandId());
        if (replyEmitter != null) {
            if (reply instanceof UnknownReply) {
                replyEmitter.onError(new ChannelUnknownCommandException(reply.getErrorDetails()));
            } else if (reply instanceof NoReply || reply instanceof IgnoredReply) {
                replyEmitter.onError(new ChannelNoReplyException(reply.getErrorDetails()));
            } else {
                ((SingleEmitter<Reply<?>>) replyEmitter).onSuccess(reply);
            }
        }
    }

    @Override
    public Completable close() {
        return Completable.fromRunnable(this::cleanChannel);
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
            this.webSocket.close((short) 1000).subscribe();
        }
    }

    /**
     * Method call to handle close event
     */
    protected void closeHandler() {}

    /**
     * Method call to handle pong frame
     */
    protected void pongHandler() {}

    /**
     * Method call to handle initialize command type
     */
    protected void handleHelloCommand(
        final CompletableEmitter emitter,
        final Command<?> command,
        final CommandHandler<Command<?>, Reply<?>> commandHandler
    ) {
        if (commandHandler != null) {
            handleCommand(command, commandHandler, false)
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
                .subscribe();
        } else {
            startPingTask();
            emitter.onComplete();
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
    protected void handleGoodByeCommand(final Command<?> command, final CommandHandler<Command<?>, Reply<?>> commandHandler) {
        if (commandHandler != null) {
            handleCommand(command, commandHandler, true).doFinally(this::cleanChannel).subscribe();
        } else {
            this.cleanChannel();
        }
    }

    protected void handleCommandAsync(final Command<?> command, final CommandHandler<Command<?>, Reply<?>> commandHandler) {
        handleCommand(command, commandHandler, false).subscribe();
    }

    protected Single<Reply<?>> handleCommand(
        final Command<?> command,
        final CommandHandler<Command<?>, Reply<?>> commandHandler,
        boolean dontWriteReply
    ) {
        return commandHandler
            .handle(command)
            .doOnSuccess(reply -> {
                if (!dontWriteReply) {
                    writeReply(reply);
                    if (reply.stopOnErrorStatus() && reply.getCommandStatus() == ERROR) {
                        webSocket.close().subscribe();
                    }
                }
            })
            .doOnError(throwable -> {
                log.warn("Unable to handle command [{}, {}]", command.getType(), command.getId());
                webSocket.close((short) 1011, "Unexpected error").subscribe();
            });
    }

    @Override
    public <C extends Command<?>, R extends Reply<?>> Single<R> send(final C command) {
        return send(command, false);
    }

    protected Single<HelloReply> sendHelloCommand(final HelloCommand helloCommand) {
        return send(helloCommand, true);
    }

    private <C extends Command<?>, R extends Reply<?>> Single<R> send(final C command, final boolean ignoreActiveStatus) {
        return Single
            .defer(() -> {
                if (!ignoreActiveStatus && !active) {
                    return Single.error(new ChannelInactiveException());
                }
                ReplyHandler<Command<?>, Command<?>, Reply<?>> replyHandler = (ReplyHandler<Command<?>, Command<?>, Reply<?>>) replyHandlers.get(
                    command.getType()
                );
                if (replyHandler != null) {
                    return replyHandler.decorate(command);
                } else {
                    return Single.just(command);
                }
            })
            .flatMap(decoratedCommand ->
                Single
                    .<R>create(emitter -> {
                        resultEmitters.put(decoratedCommand.getId(), emitter);
                        writeCommand(decoratedCommand).doOnError(emitter::onError).onErrorComplete().subscribe();
                    })
                    .doOnError(throwable ->
                        log.warn(
                            "Unable to receive reply for command [{}, {}] because channel has been closed.",
                            decoratedCommand.getType(),
                            decoratedCommand.getId()
                        )
                    )
                    .timeout(
                        decoratedCommand.getReplyTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        Single.error(() -> {
                            log.warn("No reply received in time for command [{}, {}]", command.getType(), command.getId());
                            throw new ChannelTimeoutException();
                        })
                    )
                    .compose(upstream -> {
                        ReplyHandler<Command<?>, Command<?>, Reply<?>> replyHandler = (ReplyHandler<Command<?>, Command<?>, Reply<?>>) replyHandlers.get(
                            command.getType()
                        );
                        if (replyHandler != null) {
                            return (SingleSource<R>) upstream
                                .flatMap(reply -> replyHandler.handle(reply))
                                .onErrorResumeNext(throwable -> replyHandler.handleError(command, throwable));
                        } else {
                            return upstream;
                        }
                    })
            )
            // Cleanup result emitters list if cancelled by the upstream.
            .doOnDispose(() -> resultEmitters.remove(command.getId()));
    }

    protected <C extends Command<?>> Completable writeCommand(C command) {
        ProtocolExchange protocolExchange = ProtocolExchange
            .builder()
            .type(ProtocolExchange.Type.COMMAND)
            .exchangeType(command.getType())
            .exchange(command)
            .build();
        return writeToSocket(command.getId(), command.getType(), protocolExchange);
    }

    protected <R extends Reply<?>> void writeReply(R reply) {
        ProtocolExchange protocolExchange = ProtocolExchange
            .builder()
            .type(ProtocolExchange.Type.REPLY)
            .exchangeType(reply.getType())
            .exchange(reply)
            .build();
        writeToSocket(reply.getCommandId(), reply.getType(), protocolExchange).subscribe();
    }

    private Completable writeToSocket(final String commandId, final String commandType, final ProtocolExchange websocketExchange) {
        if (!webSocket.isClosed()) {
            return webSocket
                .writeBinaryMessage(protocolAdapter.write(websocketExchange))
                .doOnComplete(() -> log.debug("Write command/reply [{}, {}] to websocket successfully", commandType, commandId))
                .onErrorResumeNext(throwable -> {
                    log.error("An error occurred when trying to send command/reply [{}, {}]", commandType, commandId);
                    return Completable.error(new Exception("Write to socket failed"));
                });
        } else {
            return Completable.error(new ChannelClosedException());
        }
    }

    @Override
    public void addCommandHandlers(final List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> commandHandlers) {
        if (commandHandlers != null) {
            commandHandlers.forEach(commandHandler -> this.commandHandlers.putIfAbsent(commandHandler.handleType(), commandHandler));
        }
    }

    @Override
    public void addReplyHandlers(final List<ReplyHandler<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> replyHandlers) {
        if (replyHandlers != null) {
            replyHandlers.forEach(replyHandler -> this.replyHandlers.putIfAbsent(replyHandler.handleType(), replyHandler));
        }
    }
}
