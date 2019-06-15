/*
 * Copyright 2019 NAVER Corp.
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

package com.navercorp.pinpoint.collector.receiver.grpc;

import com.navercorp.pinpoint.common.util.Assert;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Taejin Koo
 */
public class PinpointGrpcServerRepository {

    private final ConcurrentMap<Long, PinpointGrpcServer> grpcServerRepository = new ConcurrentHashMap<>();

    public boolean registerIfAbsent(Long transportId, PinpointGrpcServer pinpointGrpcServer) {
        Assert.requireNonNull(transportId, "transportId must not be null");

        PinpointGrpcServer old = grpcServerRepository.putIfAbsent(transportId, pinpointGrpcServer);
        return old == null;
    }

    public void unregister(Long transportId) {
        Assert.requireNonNull(transportId, "transportId must not be null");

        grpcServerRepository.remove(transportId);
    }

    public PinpointGrpcServer get(Long transportId) {
        Assert.requireNonNull(transportId, "transportId must not be null");

        return grpcServerRepository.get(transportId);
    }

}
