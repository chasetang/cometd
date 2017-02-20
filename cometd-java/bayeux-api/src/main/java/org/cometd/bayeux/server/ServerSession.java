/*
 * Copyright (c) 2008-2017 the original author or authors.
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
package org.cometd.bayeux.server;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.cometd.bayeux.Bayeux;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.Promise;
import org.cometd.bayeux.Session;

/**
 * <p>Objects implementing this interface are the server-side representation of remote Bayeux clients.</p>
 * <p>{@link ServerSession} contains the queue of messages to be delivered to the client; messages are
 * normally queued on a {@link ServerSession} by publishing them to a channel to which the session is
 * subscribed (via {@link ServerChannel#publish(Session, ServerMessage.Mutable)}.</p>
 * <p>The {@link #deliver(Session, ServerMessage.Mutable)} and {@link #deliver(Session, String, Object)}
 * methods may be used to directly queue messages to a session without publishing them to all subscribers
 * of a channel.</p>
 */
public interface ServerSession extends Session {
    /**
     * <p>Adds the given extension to this session.</p>
     *
     * @param extension the extension to add
     * @see #removeExtension(Extension)
     */
    public void addExtension(Extension extension);

    /**
     * <p>Removes the given extension from this session.</p>
     *
     * @param extension the extension to remove
     * @see #addExtension(Extension)
     */
    public void removeExtension(Extension extension);

    /**
     * @return an immutable list of extensions present in this ServerSession instance
     * @see #addExtension(Extension)
     */
    public List<Extension> getExtensions();

    /**
     * <p>Adds the given listener to this session.</p>
     *
     * @param listener the listener to add
     * @see #removeListener(ServerSessionListener)
     */
    public void addListener(ServerSessionListener listener);

    /**
     * <p>Removes the given listener from this session.</p>
     *
     * @param listener the listener to remove
     * @see #addListener(ServerSessionListener)
     */
    public void removeListener(ServerSessionListener listener);

    /**
     * @return whether this is a session for a local client on server-side
     */
    public boolean isLocalSession();

    /**
     * @return the {@link LocalSession} associated with this session,
     * or null if this is a session representing a remote client.
     */
    public LocalSession getLocalSession();

    /**
     * <p>Delivers the given message to this session.</p>
     * <p>This is different from {@link ServerChannel#publish(Session, ServerMessage.Mutable)}
     * as the message is delivered only to this session and
     * not to all subscribers of the channel.</p>
     * <p>The message should still have a channel id specified, so that the ClientSession
     * may identify the listeners the message should be delivered to.</p>
     *
     * @param sender  the session delivering the message
     * @param message the message to deliver
     * @return a future that tells whether the message has been delivered
     * @see #deliver(Session, String, Object)
     */
    public CompletableFuture<Boolean> deliver(Session sender, ServerMessage.Mutable message);

    /**
     * <p>Delivers the given information to this session.</p>
     *
     * @param sender  the session delivering the message
     * @param channel the channel of the message
     * @param data    the data of the message
     * @return a future that tells whether the message has been delivered
     * @see #deliver(Session, ServerMessage.Mutable)
     */
    public CompletableFuture<Boolean> deliver(Session sender, String channel, Object data);

    /**
     * @return the set of channels to which this session is subscribed to
     */
    public Set<ServerChannel> getSubscriptions();

    /**
     * @return The string indicating the client user agent, or null if not known
     */
    public String getUserAgent();

    /**
     * @return the period of time, in milliseconds, that the client associated with this session
     * will wait before issuing a connect message, or -1 if the default value is used
     * @see ServerTransport#getInterval()
     * @see #setInterval(long)
     */
    public long getInterval();

    /**
     * @param interval the period of time, in milliseconds, that the client
     *                 associated with this session will wait before issuing a connect message
     */
    public void setInterval(long interval);

    /**
     * @return the period of time, in milliseconds, that the server will hold connect messages
     * for this session or -1 if the default value is used
     * @see ServerTransport#getTimeout()
     */
    public long getTimeout();

    /**
     * @param timeout the period of time, in milliseconds, that the server will hold connect
     *                messages for this session
     */
    public void setTimeout(long timeout);

    /**
     * <p>Common interface for {@link ServerSession} listeners.</p>
     * <p>Specific sub-interfaces define what kind of event listeners will be notified.</p>
     */
    public interface ServerSessionListener extends Bayeux.BayeuxListener {
    }

    /**
     * <p>Listeners objects that implement this interface will be notified of session removal.</p>
     */
    public interface RemoveListener extends ServerSessionListener {
        /**
         * <p>Callback invoked when the session is removed.</p>
         *
         * @param session the removed session
         * @param timeout whether the session has been removed because of a timeout
         */
        public void removed(ServerSession session, boolean timeout);
    }

    /**
     * <p>Listeners objects that implement this interface will be notified of message sending.</p>
     */
    public interface MessageListener extends ServerSessionListener {
        public default void onMessage(ServerSession session, ServerSession sender, ServerMessage message, Promise<Boolean> promise) {
            promise.succeed(onMessage(session, sender, message));
        }

        /**
         * <p>Callback invoked when a message is sent.</p>
         * <p>Implementers can decide to return false to signal that the message should not
         * be further processed, meaning that other session listeners will not be notified
         * and that the message will be discarded for this session.</p>
         *
         * @param session the session that will receive the message
         * @param sender  the session that sent the message
         * @param message the message sent
         * @return whether the processing of the message should continue
         */
        public default boolean onMessage(ServerSession session, ServerSession sender, ServerMessage message) {
            return true;
        }
    }

    /**
     * <p>Listener objects that implement this interface will be notified when a message
     * is queued in the session queue.</p>
     * <p>This is a <em>restricted</em> listener interface, see {@link MaxQueueListener}.</p>
     */
    public interface QueueListener extends ServerSessionListener {
        /**
         * <p>Callback invoked when a message is queued in the session queue.</p>
         *
         * @param sender  the ServerSession that sends the message, may be null.
         * @param message the message being queued
         */
        public void queued(ServerSession sender, ServerMessage message);
    }

    /**
     * <p>Listeners objects that implement this interface will be notified when the session queue
     * is being drained to actually deliver the messages.</p>
     * <p>This is a <em>restricted</em> listener interface, see {@link MaxQueueListener}.</p>
     */
    public interface DeQueueListener extends ServerSessionListener {
        /**
         * <p>Callback invoked to notify that the queue of messages is about to be sent to the
         * remote client.</p>
         * <p>This is the last chance to process the queue and remove duplicates or merge messages.</p>
         *
         * @param session the session whose messages are being sent
         * @param queue   the queue of messages to send
         */
        public void deQueue(ServerSession session, Queue<ServerMessage> queue);
    }

    /**
     * <p>Listeners objects that implement this interface will be notified when the session queue is full.</p>
     * <p>This is a <em>restricted</em> listener interface because implementers are invoked while holding
     * the session lock and therefore are restricted in the type of operations they may perform; in particular,
     * publishing a message to another session may end up in a deadlock.</p>
     */
    public interface MaxQueueListener extends ServerSessionListener {
        /**
         * <p>Callback invoked to notify when the message queue is exceeding the value
         * configured for the transport with the option "maxQueue".</p>
         * <p>Implementers may modify the queue, for example by removing or merging messages.</p>
         *
         * @param session the session that will receive the message
         * @param queue   the session's message queue
         * @param sender  the session that is sending the messages
         * @param message the message that exceeded the max queue capacity
         * @return true if the message should be added to the session queue
         */
        public boolean queueMaxed(ServerSession session, Queue<ServerMessage> queue, ServerSession sender, Message message);
    }

    /**
     * <p>Extension API for {@link ServerSession}.</p>
     * <p>Implementations of this interface allow to modify incoming and outgoing messages
     * respectively just before and just after they are handled by the implementation,
     * either on client side or server side.</p>
     * <p>Extensions are be registered in order and one extension may allow subsequent
     * extensions to process the message by returning true from the callback method, or
     * forbid further processing by returning false.</p>
     *
     * @see ServerSession#addExtension(Extension)
     * @see BayeuxServer.Extension
     */
    public interface Extension {
        default void incoming(ServerSession session, ServerMessage.Mutable message, Promise<Boolean> promise) {
            promise.succeed(message.isMeta() ? rcvMeta(session, message) : rcv(session, message));
        }

        /**
         * <p>Callback method invoked every time a normal message is incoming.</p>
         *
         * @param session the session that sent the message
         * @param message the incoming message
         * @return true if message processing should continue, false if it should stop
         */
        default boolean rcv(ServerSession session, ServerMessage.Mutable message) {
            return true;
        }

        /**
         * <p>Callback method invoked every time a meta message is incoming.</p>
         *
         * @param session the session that is sent the message
         * @param message the incoming meta message
         * @return true if message processing should continue, false if it should stop
         */
        default boolean rcvMeta(ServerSession session, ServerMessage.Mutable message) {
            return true;
        }

        default void outgoing(ServerSession session, ServerMessage.Mutable message, Promise<ServerMessage.Mutable> promise) {
            if (message.isMeta()) {
                promise.succeed(sendMeta(session, message) ? message : null);
            } else {
                ServerMessage result = send(session, message);
                if (result instanceof ServerMessage.Mutable) {
                    promise.succeed((ServerMessage.Mutable)result);
                } else if (result == null) {
                    promise.succeed(null);
                } else {
                    promise.fail(new IllegalArgumentException());
                }
            }
        }

        /**
         * <p>Callback method invoked every time a normal message is outgoing.</p>
         *
         * @param session the session receiving the message
         * @param message the outgoing message
         * @return The message to send or null to not send the message
         */
        default ServerMessage send(ServerSession session, ServerMessage message) {
            return message;
        }

        /**
         * <p>Callback method invoked every time a meta message is outgoing.</p>
         *
         * @param session the session receiving the message
         * @param message the outgoing meta message
         * @return true if message processing should continue, false if it should stop
         */
        default boolean sendMeta(ServerSession session, ServerMessage.Mutable message) {
            return true;
        }

        /**
         * Empty implementation of {@link Extension}.
         * @deprecated
         */
        @Deprecated
        public static class Adapter implements Extension {
        }
    }
}
