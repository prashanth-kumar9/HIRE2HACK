package com.notificationsystem.service;

import com.notificationsystem.model.ChannelType;
import com.notificationsystem.model.UserPreference;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserPreferenceService {
    private final Map<String, UserPreference> preferences = new ConcurrentHashMap<>();

    public void savePreference(UserPreference preference) {
        preferences.put(preference.getUserId(), preference);
    }

    public UserPreference getPreference(String userId) {
        return preferences.getOrDefault(
                userId,
                new UserPreference(userId, Set.of(ChannelType.EMAIL))
        );
    }

    public void removePreference(String userId) {
        preferences.remove(userId);
    }
}