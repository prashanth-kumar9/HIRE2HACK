package com.notificationsystem.factory;

import com.notificationsystem.channel.NotificationChannel;
import com.notificationsystem.model.ChannelType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationChannelFactory {
    private final Map<ChannelType, NotificationChannel> channelMap = new ConcurrentHashMap<>();

    @Autowired
    public NotificationChannelFactory(
            com.notificationsystem.channel.EmailNotificationChannel emailChannel,
            com.notificationsystem.channel.SmsNotificationChannel smsChannel,
            com.notificationsystem.channel.PushNotificationChannel pushChannel) {
        channelMap.put(ChannelType.EMAIL, emailChannel);
        channelMap.put(ChannelType.SMS, smsChannel);
        channelMap.put(ChannelType.PUSH, pushChannel);
    }

    public NotificationChannel getChannel(ChannelType channelType) {
        NotificationChannel channel = channelMap.get(channelType);
        if (channel == null) {
            throw new IllegalArgumentException("Unsupported channel type: " + channelType);
        }
        return channel;
    }
}