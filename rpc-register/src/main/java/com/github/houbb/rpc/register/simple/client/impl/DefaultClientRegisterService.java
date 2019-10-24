/*
 * Copyright (c)  2019. houbinbin Inc.
 * rpc All rights reserved.
 */

package com.github.houbb.rpc.register.simple.client.impl;

import com.github.houbb.heaven.util.common.ArgUtil;
import com.github.houbb.heaven.util.guava.Guavas;
import com.github.houbb.heaven.util.lang.ObjectUtil;
import com.github.houbb.heaven.util.util.CollectionUtil;
import com.github.houbb.log.integration.core.Log;
import com.github.houbb.log.integration.core.LogFactory;
import com.github.houbb.rpc.register.domain.entry.ServerEntry;
import com.github.houbb.rpc.register.domain.message.RegisterMessage;
import com.github.houbb.rpc.register.domain.message.impl.RegisterMessages;
import com.github.houbb.rpc.register.simple.client.ClientRegisterService;
import com.github.houbb.rpc.register.simple.constant.RegisterMessageTypeConst;
import io.netty.channel.Channel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p> 默认客户端注册服务实现类 </p>
 *
 * <pre> Created: 2019/10/23 9:42 下午  </pre>
 * <pre> Project: rpc  </pre>
 *
 * @author houbinbin
 * @since 0.0.8
 */
public class DefaultClientRegisterService implements ClientRegisterService {

    private static final Log LOG = LogFactory.getLog(DefaultClientRegisterService.class);

    /**
     * 服务信息-客户端列表 map
     * key: serviceId
     * value: 对应的客户端列表信息。
     *
     * 客户端使用定期拉取的方式：
     * （1）传入 host 信息，返回对应的 service 列表。
     * （2）根据 service 列表，变化时定期推送给客户端。
     *
     * 只是在第一次采用拉取的方式，后面全部采用推送的方式。
     * （1）只有变更的时候，才会进行推送，保证实时性。
     * （2）客户端启动时拉取，作为保底措施。避免客户端不在线等情况。
     *
     * @since 0.0.8
     */
    private final Map<String, Set<Channel>> serviceClientChannelMap;

    public DefaultClientRegisterService() {
        this.serviceClientChannelMap = new ConcurrentHashMap<>();
    }

    @Override
    public void subscribe(ServerEntry clientEntry, Channel clientChannel) {
        paramCheck(clientEntry);

        final String serviceId = clientEntry.serviceId();
        Set<Channel> channelSet = serviceClientChannelMap.get(serviceId);
        if (ObjectUtil.isNull(channelSet)) {
            channelSet = Guavas.newHashSet();
        }
        channelSet.add(clientChannel);
        serviceClientChannelMap.put(serviceId, channelSet);
    }

    @Override
    public void unSubscribe(ServerEntry clientEntry, Channel clientChannel) {
        paramCheck(clientEntry);

        final String serviceId = clientEntry.serviceId();
        Set<Channel> channelSet = serviceClientChannelMap.get(serviceId);

        if (CollectionUtil.isEmpty(channelSet)) {
            // 服务列表为空
            LOG.info("[Register Client] remove host set is empty. entry: {}", clientEntry);
            return;
        }

        channelSet.remove(clientChannel);
        serviceClientChannelMap.put(serviceId, channelSet);
    }

    @Override
    public void notify(String serviceId, List<ServerEntry> serverEntryList) {
        ArgUtil.notEmpty(serviceId, "serviceId");

        List<Channel> clientChannelList = clientChannelList(serviceId);
        if (CollectionUtil.isEmpty(clientChannelList)) {
            LOG.info("[Register] notify clients is empty for service: {}",
                    serviceId);
            return;
        }

        // 循环通知
        for(Channel channel : clientChannelList) {
            RegisterMessage registerMessage = RegisterMessages.of(RegisterMessageTypeConst.REGISTER_NOTIFY, serverEntryList);
            channel.writeAndFlush(registerMessage);
        }
    }

    /**
     * 参数校验
     *
     * @param serverEntry 入参信息
     * @since 0.0.8
     */
    private void paramCheck(final ServerEntry serverEntry) {
        ArgUtil.notNull(serverEntry, "serverEntry");
        ArgUtil.notEmpty(serverEntry.serviceId(), "serverEntry.serviceId");
        ArgUtil.notEmpty(serverEntry.ip(), "serverEntry.ip");
    }

    /**
     * 获取所有的客户端列表
     * @param serviceId 服务标识
     * @return 客户端列表标识
     * @since 0.0.8
     */
    private List<Channel> clientChannelList(String serviceId) {
        ArgUtil.notEmpty(serviceId, "serviceId");

        Set<Channel> clientSet = serviceClientChannelMap.get(serviceId);
        return Guavas.newArrayList(clientSet);
    }

}
