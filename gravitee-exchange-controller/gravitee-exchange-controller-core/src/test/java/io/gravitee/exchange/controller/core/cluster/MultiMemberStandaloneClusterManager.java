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
package io.gravitee.exchange.controller.core.cluster;

import io.gravitee.node.api.cluster.Member;
import io.gravitee.node.api.cluster.MemberListener;
import io.gravitee.node.plugin.cluster.standalone.StandaloneClusterManager;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MultiMemberStandaloneClusterManager extends StandaloneClusterManager {

    private final Set<Member> members = new HashSet<>();
    private final List<MemberListener> memberListeners = new ArrayList<>();

    public MultiMemberStandaloneClusterManager(final Vertx vertx) {
        super(vertx);
    }

    @Override
    public void addMemberListener(final MemberListener listener) {
        memberListeners.add(listener);
    }

    @Override
    public void removeMemberListener(final MemberListener listener) {
        memberListeners.remove(listener);
    }

    @Override
    public Set<Member> members() {
        return members;
    }

    public void addMember(Member member) {
        members.add(member);
        memberListeners.forEach(memberListener -> memberListener.onMemberAdded(member));
    }

    public void removeMember(Member member) {
        members.remove(member);
        memberListeners.forEach(memberListener -> memberListener.onMemberRemoved(member));
    }
}
