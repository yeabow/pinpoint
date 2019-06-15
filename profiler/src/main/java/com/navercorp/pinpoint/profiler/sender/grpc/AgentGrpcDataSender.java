/*
 * Copyright 2019 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.profiler.sender.grpc;

import com.navercorp.pinpoint.grpc.trace.MetadataGrpc;
import com.navercorp.pinpoint.profiler.context.active.ActiveTraceRepository;
import com.navercorp.pinpoint.profiler.receiver.grpc.GrpcCommandService;
import com.navercorp.pinpoint.profiler.sender.EnhancedDataSender;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.GeneratedMessageV3;
import com.navercorp.pinpoint.common.util.Assert;
import com.navercorp.pinpoint.common.util.ExecutorFactory;
import com.navercorp.pinpoint.common.util.PinpointThreadFactory;
import com.navercorp.pinpoint.common.util.StringUtils;
import com.navercorp.pinpoint.grpc.AgentHeaderFactory;
import com.navercorp.pinpoint.grpc.HeaderFactory;
import com.navercorp.pinpoint.grpc.client.ChannelFactory;
import com.navercorp.pinpoint.grpc.trace.AgentGrpc;
import com.navercorp.pinpoint.grpc.trace.PAgentInfo;
import com.navercorp.pinpoint.grpc.trace.PApiMetaData;
import com.navercorp.pinpoint.grpc.trace.PResult;
import com.navercorp.pinpoint.grpc.trace.PSqlMetaData;
import com.navercorp.pinpoint.grpc.trace.PStringMetaData;
import com.navercorp.pinpoint.profiler.context.thrift.MessageConverter;
import com.navercorp.pinpoint.profiler.sender.AsyncQueueingExecutor;
import com.navercorp.pinpoint.profiler.sender.AsyncQueueingExecutorListener;
import com.navercorp.pinpoint.profiler.sender.DefaultAsyncQueueingExecutorListener;
import com.navercorp.pinpoint.profiler.sender.RequestMessage;
import com.navercorp.pinpoint.profiler.sender.RequestMessageFactory;
import com.navercorp.pinpoint.profiler.sender.RetryMessage;
import com.navercorp.pinpoint.profiler.sender.RetryQueue;
import com.navercorp.pinpoint.profiler.sender.WriteFailFutureListener;
import com.navercorp.pinpoint.rpc.DefaultFuture;
import com.navercorp.pinpoint.rpc.Future;
import com.navercorp.pinpoint.rpc.FutureListener;
import com.navercorp.pinpoint.rpc.ResponseMessage;
import com.navercorp.pinpoint.rpc.client.PinpointClientReconnectEventListener;
import com.navercorp.pinpoint.rpc.util.TimerFactory;
import io.grpc.ManagedChannel;
import io.grpc.NameResolverProvider;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jaehong.kim
 */
public class AgentGrpcDataSender implements EnhancedDataSender {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    static {
        // preClassLoad
        ChannelBuffers.buffer(2);
    }

    private final Timer timer;

    private final AtomicBoolean fireState = new AtomicBoolean(false);

    private final WriteFailFutureListener writeFailFutureListener;

    private final RetryQueue retryQueue = new RetryQueue();

    protected final AsyncQueueingExecutor<Object> asyncQueueingExecutor;

    protected final String name;
    protected final ManagedChannel managedChannel;

    // not thread safe
    protected final MessageConverter<GeneratedMessageV3> messageConverter;

    protected final ChannelFactory channelFactory;

    protected volatile boolean shutdown;
    protected final ThreadPoolExecutor executor;

    private final AgentGrpc.AgentFutureStub agentStub;
    private final MetadataGrpc.MetadataFutureStub metadataStub;

    private GrpcCommandService grpcCommandService;


    public AgentGrpcDataSender(String name, String host, int port, MessageConverter<GeneratedMessageV3> messageConverter, HeaderFactory<AgentHeaderFactory.Header> headerFactory, NameResolverProvider nameResolverProvider) {
        this(name, host, port, messageConverter, headerFactory, nameResolverProvider, null);
    }

    public AgentGrpcDataSender(String name, String host, int port, MessageConverter<GeneratedMessageV3> messageConverter, HeaderFactory<AgentHeaderFactory.Header> headerFactory, NameResolverProvider nameResolverProvider, ActiveTraceRepository activeTraceRepository) {
        this.name = Assert.requireNonNull(name, "name must not be null");
        this.messageConverter = Assert.requireNonNull(messageConverter, "messageConverter must not be null");

        this.timer = createTimer(name);
        this.writeFailFutureListener = new WriteFailFutureListener(logger, "io write fail.", host + ":" + port);

        final String executorName = getExecutorName(name);
        this.asyncQueueingExecutor = createAsyncQueueingExecutor(1024 * 5, executorName);
        this.executor = newExecutorService(name);

        this.channelFactory = newChannelFactory(name, headerFactory, nameResolverProvider);
        this.managedChannel = channelFactory.build(name, host, port);
        this.agentStub = AgentGrpc.newFutureStub(managedChannel);
        this.metadataStub = MetadataGrpc.newFutureStub(managedChannel);

        this.grpcCommandService = new GrpcCommandService(managedChannel, GrpcDataSender.reconnectScheduler, activeTraceRepository);
    }

    private ThreadPoolExecutor newExecutorService(String name) {
        ThreadFactory threadFactory = new PinpointThreadFactory(name, true);
        return ExecutorFactory.newFixedThreadPool(1, 1000, threadFactory);
    }

    private Timer createTimer(String name) {
        final String timerName = getTimerName(name);

        HashedWheelTimer timer = TimerFactory.createHashedWheelTimer(timerName, 100, TimeUnit.MILLISECONDS, 512);
        timer.start();
        return timer;
    }

    private String getTimerName(String name) {
        name = StringUtils.defaultString(name, "DEFAULT");
        return String.format("Pinpoint-AgentGrpcDataSender(%s)-Timer", name);
    }

    private String getExecutorName(String name) {
        name = StringUtils.defaultString(name, "DEFAULT");
        return String.format("Pinpoint-AgentGrpcDataSender(%s)-Executor", name);
    }

    private AsyncQueueingExecutor<Object> createAsyncQueueingExecutor(int queueSize, String executorName) {
        AsyncQueueingExecutorListener<Object> listener = new DefaultAsyncQueueingExecutorListener() {
            @Override
            public void execute(Object message) {
                sendPacket(message);
            }
        };
        final AsyncQueueingExecutor<Object> executor = new AsyncQueueingExecutor<Object>(queueSize, executorName, listener);
        return executor;
    }

    private ChannelFactory newChannelFactory(String name, HeaderFactory<AgentHeaderFactory.Header> headerFactory, NameResolverProvider nameResolverProvider) {
        return new ChannelFactory(name, headerFactory, nameResolverProvider);
    }

    @Override
    public boolean request(Object data) {
        return this.request(data, 3);
    }

    @Override
    public boolean request(Object data, int retryCount) {
        final RequestMessage<?> message = RequestMessageFactory.request(data, retryCount);
        return asyncQueueingExecutor.execute(message);
    }

    @Override
    public boolean request(Object data, FutureListener listener) {
        final RequestMessage<Object> message = RequestMessageFactory.request(data, listener);
        return asyncQueueingExecutor.execute(message);
    }

    @Override
    public boolean send(Object data) {
        return false;
    }

    @Override
    public void stop() {
        if (grpcCommandService != null) {
            grpcCommandService.stop();
        }

        asyncQueueingExecutor.stop();

        Set<Timeout> stop = timer.stop();
        if (!stop.isEmpty()) {
            logger.info("stop Timeout:{}", stop.size());
        }

        shutdown = true;
        if (this.managedChannel != null) {
            this.managedChannel.shutdown();
        }
        this.channelFactory.close();
    }

    @Override
    public boolean addReconnectEventListener(PinpointClientReconnectEventListener eventListener) {
        // TODO
        return false;
    }

    @Override
    public boolean removeReconnectEventListener(PinpointClientReconnectEventListener eventListener) {
        // TODO
        return false;
    }

    protected void sendPacket(Object message) {
        try {
            if (message instanceof RequestMessage<?>) {
                final RequestMessage<?> requestMessage = (RequestMessage<?>) message;
                if (doRequest(requestMessage)) {
                    return;
                }
            } else {
                logger.error("sendPacket fail. invalid dto type:{}", message.getClass());
                return;
            }
        } catch (Exception e) {
            logger.warn("tcp send fail. Caused:{}", e.getMessage(), e);
        }
    }

    private boolean doRequest(RequestMessage<?> requestMessage) {
        Object message = requestMessage.getMessage();
        if (!(message instanceof GeneratedMessageV3)) {
            message = this.messageConverter.toMessage(requestMessage.getMessage());
        }

        final FutureListener futureListener = requestMessage.getFutureListener();
        if (futureListener != null) {
            doRequest(message, futureListener);
        } else {
            int retryCount = requestMessage.getRetryCount();
            doRequest(message, retryCount, message);
        }

        return true;
    }

    // Separate doRequest method to avoid creating unnecessary objects. (Generally, sending message is successed when firt attempt.)
    private void doRequest(final Object requestPacket, final int maxRetryCount, final Object targetClass) {
        FutureListener futureListener = (new FutureListener<ResponseMessage>() {
            @Override
            public void onComplete(Future<ResponseMessage> future) {
                if (future.isSuccess()) {
                    // Should cache?
                    ResponseMessage responseMessage = future.getResult();
                    try {
                        PResult result = PResult.parseFrom(responseMessage.getMessage());
                        if (result.getSuccess()) {
                            logger.debug("result success");
                        } else {
                            logger.info("request fail. request:{} Caused:{}", targetClass, result.getMessage());
                            RetryMessage retryMessage = new RetryMessage(1, maxRetryCount, requestPacket, targetClass.getClass().getSimpleName());
                            retryRequest(retryMessage);
                        }
                    } catch (Exception e) {
                        logger.warn("Invalid response:{}", responseMessage);
                    }
                } else {
                    logger.info("request fail. request:{} Caused:{}", targetClass, future.getCause().getMessage(), future.getCause());
                    RetryMessage retryMessage = new RetryMessage(1, maxRetryCount, requestPacket, targetClass.getClass().getSimpleName());
                    retryRequest(retryMessage);
                }
            }
        });

        doRequest(requestPacket, futureListener);
    }

    // Separate doRequest method to avoid creating unnecessary objects. (Generally, sending message is successed when firt attempt.)
    private void doRequest(final RetryMessage retryMessage) {
        FutureListener futureListener = (new FutureListener<ResponseMessage>() {
            @Override
            public void onComplete(Future<ResponseMessage> future) {
                if (future.isSuccess()) {
                    // Should cache?
                    ResponseMessage responseMessage = future.getResult();
                    try {
                        PResult result = PResult.parseFrom(responseMessage.getMessage());
                        if (result.getSuccess()) {
                            logger.debug("result success");
                        } else {
                            logger.info("request fail. request:{}, Caused:{}", retryMessage, result.getMessage());
                            retryRequest(retryMessage);
                        }
                    } catch (Exception e) {
                        logger.warn("Invalid response:{}", responseMessage);
                    }
                } else {
                    logger.info("request fail. request:{}, caused:{}", retryMessage, future.getCause().getMessage(), future.getCause());
                    retryRequest(retryMessage);
                }
            }
        });

        doRequest(retryMessage.getBytes(), futureListener);
    }

    private void retryRequest(RetryMessage retryMessage) {
        retryQueue.add(retryMessage);
        if (fireTimeout()) {
            timer.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    while (true) {
                        RetryMessage retryMessage = retryQueue.get();
                        if (retryMessage == null) {
                            // Maybe concurrency issue. But ignore it because it's unlikely.
                            fireComplete();
                            return;
                        }
                        int fail = retryMessage.fail();
                        doRequest(retryMessage);
                    }
                }
            }, 1000 * 10, TimeUnit.MILLISECONDS);
        }
    }

    private void doRequest(final Object requestPacket, final FutureListener futureListener) {
        ListenableFuture<PResult> future = toProtocolBuf(requestPacket);
        if (future == null) {
            logger.debug("Failed to protocol buffers");
            return;
        }

        Futures.addCallback(future, new FutureCallback<PResult>() {
            @Override
            public void onSuccess(@Nullable PResult pResult) {
                DefaultFuture<ResponseMessage> future = new DefaultFuture<ResponseMessage>();
                ResponseMessage responseMessage = new ResponseMessage();
                responseMessage.setMessage(pResult.toByteArray());
                future.setResult(responseMessage);
                future.setListener(futureListener);
            }

            @Override
            public void onFailure(Throwable throwable) {
                DefaultFuture<ResponseMessage> future = new DefaultFuture<ResponseMessage>();
                future.setFailure(throwable);
                future.setListener(futureListener);
            }
        }, this.executor);
    }

    private ListenableFuture<PResult> toProtocolBuf(final Object requestPacket) {
        if (requestPacket instanceof PAgentInfo) {
            PAgentInfo agentInfo = (PAgentInfo) requestPacket;
            return this.agentStub.requestAgentInfo(agentInfo);
        } else if (requestPacket instanceof PSqlMetaData) {
            PSqlMetaData sqlMetaData = (PSqlMetaData) requestPacket;
            return this.metadataStub.requestSqlMetaData(sqlMetaData);
        } else if (requestPacket instanceof PApiMetaData) {
            final PApiMetaData apiMetaData = (PApiMetaData) requestPacket;
            return this.metadataStub.requestApiMetaData(apiMetaData);
        } else if (requestPacket instanceof PStringMetaData) {
            final PStringMetaData stringMetaData = (PStringMetaData) requestPacket;
            return this.metadataStub.requestStringMetaData(stringMetaData);
        }

        return null;
    }

    private boolean fireTimeout() {
        if (fireState.compareAndSet(false, true)) {
            return true;
        } else {
            return false;
        }
    }

    private void fireComplete() {
        logger.debug("fireComplete");
        fireState.compareAndSet(true, false);
    }
}