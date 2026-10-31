package com.notificationsystem.service;

import com.notificationsystem.channel.NotificationChannel;
import com.notificationsystem.factory.NotificationChannelFactory;
import com.notificationsystem.model.ChannelType;
import com.notificationsystem.model.Notification;
import com.notificationsystem.model.UserPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final UserPreferenceService preferenceService;
    private final NotificationChannelFactory channelFactory;

    @Autowired
    public NotificationDispatcher(UserPreferenceService preferenceService, NotificationChannelFactory channelFactory) {
        this.preferenceService = preferenceService;
        this.channelFactory = channelFactory;
    }

    public void dispatch(Notification notification) {
        UserPreference preference = preferenceService.getPreference(notification.getUserId());
        Set<ChannelType> channels = preference.getPreferredChannels();

        log.info("Dispatching notification to user {} via channels: {}", notification.getUserId(), channels);

        for (ChannelType channelType : channels) {
            try {
                NotificationChannel channel = channelFactory.getChannel(channelType);
                channel.send(notification);
                log.debug("Successfully sent notification via {}", channelType);
            } catch (Exception e) {
                log.error("Failed to send notification via {}: {}", channelType, e.getMessage());
            }
        }
    }
}