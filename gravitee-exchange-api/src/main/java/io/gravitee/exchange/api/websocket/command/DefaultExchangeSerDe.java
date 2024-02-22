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
package io.gravitee.exchange.api.websocket.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.Exchange;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeReply;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckCommand;
import io.gravitee.exchange.api.command.healtcheck.HealthCheckReply;
import io.gravitee.exchange.api.command.hello.HelloCommand;
import io.gravitee.exchange.api.command.hello.HelloReply;
import io.gravitee.exchange.api.command.noreply.NoReply;
import io.gravitee.exchange.api.command.primary.PrimaryCommand;
import io.gravitee.exchange.api.command.primary.PrimaryReply;
import io.gravitee.exchange.api.command.unknown.UnknownCommand;
import io.gravitee.exchange.api.command.unknown.UnknownReply;
import io.gravitee.exchange.api.websocket.command.exception.DeserializationException;
import io.gravitee.exchange.api.websocket.command.exception.SerializationException;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.gravitee.exchange.api.websocket.protocol.legacy.ignored.IgnoredReply;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultExchangeSerDe implements ExchangeSerDe {

    private static final Map<String, Class<? extends Command<?>>> DEFAULT_COMMAND_TYPE = Map.of(
        // Command
        HelloCommand.COMMAND_TYPE,
        HelloCommand.class,
        GoodByeCommand.COMMAND_TYPE,
        GoodByeCommand.class,
        io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloCommand.COMMAND_TYPE,
        io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloCommand.class,
        io.gravitee.exchange.api.websocket.protocol.legacy.goodbye.GoodByeCommand.COMMAND_TYPE,
        io.gravitee.exchange.api.websocket.protocol.legacy.goodbye.GoodByeCommand.class,
        HealthCheckCommand.COMMAND_TYPE,
        HealthCheckCommand.class,
        PrimaryCommand.COMMAND_TYPE,
        PrimaryCommand.class,
        UnknownCommand.COMMAND_TYPE,
        UnknownCommand.class
    );

    private static final Map<String, Class<? extends Reply<?>>> DEFAULT_REPLY_TYPE = Map.of(
        HelloCommand.COMMAND_TYPE,
        HelloReply.class,
        GoodByeCommand.COMMAND_TYPE,
        GoodByeReply.class,
        io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloReply.COMMAND_TYPE,
        io.gravitee.exchange.api.websocket.protocol.legacy.hello.HelloReply.class,
        io.gravitee.exchange.api.websocket.protocol.legacy.goodbye.GoodByeReply.COMMAND_TYPE,
        io.gravitee.exchange.api.websocket.protocol.legacy.goodbye.GoodByeReply.class,
        HealthCheckCommand.COMMAND_TYPE,
        HealthCheckReply.class,
        PrimaryCommand.COMMAND_TYPE,
        PrimaryReply.class,
        NoReply.COMMAND_TYPE,
        NoReply.class,
        IgnoredReply.COMMAND_TYPE,
        IgnoredReply.class,
        UnknownCommand.COMMAND_TYPE,
        UnknownReply.class
    );
    private final ObjectMapper objectMapper;

    public DefaultExchangeSerDe(final ObjectMapper objectMapper) {
        this(objectMapper, null, null);
    }

    public DefaultExchangeSerDe(
        final ObjectMapper objectMapper,
        final Map<String, Class<? extends Command<?>>> customCommandTypes,
        final Map<String, Class<? extends Reply<?>>> customReplyTypes
    ) {
        this.objectMapper = objectMapper;

        registerCommandTypes(objectMapper, customCommandTypes);
        registerReplyTypes(objectMapper, customReplyTypes);
    }

    private void registerCommandTypes(final ObjectMapper objectMapper, final Map<String, Class<? extends Command<?>>> customCommandTypes) {
        Map<String, Class<? extends Command<?>>> commandTypes = new HashMap<>(DEFAULT_COMMAND_TYPE);
        if (customCommandTypes != null) {
            commandTypes.putAll(customCommandTypes);
        }
        commandTypes.forEach((type, aClass) -> objectMapper.registerSubtypes(new NamedType(aClass, type)));
    }

    private void registerReplyTypes(final ObjectMapper objectMapper, final Map<String, Class<? extends Reply<?>>> customReplyTypes) {
        Map<String, Class<? extends Reply<?>>> replyTypes = new HashMap<>(DEFAULT_REPLY_TYPE);
        if (customReplyTypes != null) {
            replyTypes.putAll(customReplyTypes);
        }
        replyTypes.forEach((type, aClass) -> objectMapper.registerSubtypes(new NamedType(aClass, type)));
    }

    @Override
    public <C extends Command<?>> C deserializeAsCommand(final ProtocolVersion protocolVersion, final String dataType, final String data)
        throws DeserializationException {
        return (C) readCommand(data, Command.class);
    }

    protected <C extends Command<?>> C readCommand(final String data, Class<C> clazz) {
        try {
            return readJson(data, clazz);
        } catch (InvalidTypeIdException e) {
            return (C) unknownCommand();
        } catch (JsonProcessingException e) {
            throw new DeserializationException(e);
        }
    }

    protected Command<?> unknownCommand() {
        return new UnknownCommand();
    }

    @Override
    public <R extends Reply<?>> R deserializeAsReply(final ProtocolVersion protocolVersion, final String dataType, final String data)
        throws DeserializationException {
        return (R) readReply(data, Reply.class);
    }

    protected <R extends Reply<?>> R readReply(final String data, Class<R> clazz) {
        try {
            return readJson(data, clazz);
        } catch (InvalidTypeIdException e) {
            return (R) unknownReply();
        } catch (JsonProcessingException e) {
            throw new DeserializationException(e);
        }
    }

    private static UnknownReply unknownReply() {
        return new UnknownReply(null, "Unknown reply type");
    }

    protected <T> T readJson(final String jsonAsString, Class<T> clazz) throws JsonProcessingException {
        if (jsonAsString != null) {
            return objectMapper.readValue(jsonAsString, clazz);
        }
        return null;
    }

    @Override
    public String serialize(final ProtocolVersion protocolVersion, final Exchange<?> exchange) throws SerializationException {
        return writeJson(exchange);
    }

    protected String writeJson(final Object object) {
        try {
            if (object != null) {
                return objectMapper.writeValueAsString(object);
            }
            return null;
        } catch (JsonProcessingException e) {
            throw new SerializationException(e);
        }
    }
}
