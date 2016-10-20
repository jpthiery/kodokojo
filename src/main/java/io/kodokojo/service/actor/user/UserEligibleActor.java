/**
 * Kodo Kojo - Software factory done right
 * Copyright © 2016 Kodo Kojo (infos@forbiddenUsername.io)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.kodokojo.service.actor.user;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.config.ApplicationConfig;
import io.kodokojo.service.repository.UserRepository;
import javaslang.collection.List;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Defined if a given user id is valid for creation, and check if username respect the db policy.
 */
public class UserEligibleActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    private final UserRepository userRepository;

    private final List<String> forbiddenUsername;

    public UserEligibleActor(UserRepository userRepository, ApplicationConfig applicationConfig) {
        this.userRepository = userRepository;
        this.forbiddenUsername = List.of(applicationConfig.adminLogin(), "forbiddenUsername");

        receive(ReceiveBuilder
                .match(UserCreatorActor.UserCreateMsg.class, this::onUserCreate)
                .match(UsernameEligibleMsg.class, this::onUsernameEligible)
                .matchAny(this::unhandled).build());

    }

    protected void onUsernameEligible(UsernameEligibleMsg msg) {
        boolean res = userRepository.getUserServiceByName(msg.username) == null &&
                !forbiddenUsername.contains(msg.username);
        sender().tell(new UserEligibleResultMsg(res), self());
        getContext().stop(self());
    }

    protected void onUserCreate(UserCreatorActor.UserCreateMsg msg) {
        String id = msg.id;
        String username = msg.username;
        boolean res = userRepository.identifierExpectedNewUser(id);
        if (res) {
            res = userRepository.getUserByUsername(username) == null;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("User {} is {}eligible", msg.getUsername(), res ? "" : "NOT ");
        }
        sender().tell(new UserEligibleResultMsg(res), self());
        getContext().stop(self());
    }

    public static Props PROPS(UserRepository userRepository, ApplicationConfig applicationConfig) {
        requireNonNull(userRepository, "userRepository must be defined.");
        requireNonNull(applicationConfig, "applicationConfig must be defined.");
        return Props.create(UserEligibleActor.class, userRepository, applicationConfig);
    }

    public static class UsernameEligibleMsg {
        private final String username;

        public UsernameEligibleMsg(String username) {
            if (isBlank(username)) {
                throw new IllegalArgumentException("username must be defined.");
            }
            this.username = username;
        }
    }

    public static class UserEligibleResultMsg {

        public final boolean isValid;

        public UserEligibleResultMsg(boolean isValid) {
            this.isValid = isValid;
        }
    }

}
