package com.notificationsystem.service;

import com.notificationsystem.model.ChannelType;
import com.notificationsystem.model.UserPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserPreferenceServiceTest {

    private UserPreferenceService preferenceService;

    @BeforeEach
    void setUp() {
        preferenceService = new UserPreferenceService();
    }

    @Test
    void saveAndGetPreference() {
        UserPreference preference = new UserPreference("user123", Set.of(ChannelType.EMAIL, ChannelType.SMS));
        preferenceService.savePreference(preference);

        UserPreference retrieved = preferenceService.getPreference("user123");

        assertThat(retrieved.getUserId()).isEqualTo("user123");
        assertThat(retrieved.getPreferredChannels()).containsExactlyInAnyOrder(ChannelType.EMAIL, ChannelType.SMS);
    }

    @Test
    void getDefaultPreferenceWhenNotExists() {
        UserPreference preference = preferenceService.getPreference("nonexistent");

        assertThat(preference.getUserId()).isEqualTo("nonexistent");
        assertThat(preference.getPreferredChannels()).containsExactly(ChannelType.EMAIL);
    }

    @Test
    void removePreference() {
        UserPreference preference = new UserPreference("user123", Set.of(ChannelType.EMAIL));
        preferenceService.savePreference(preference);

        preferenceService.removePreference("user123");

        UserPreference retrieved = preferenceService.getPreference("user123");
        assertThat(retrieved.getPreferredChannels()).containsExactly(ChannelType.EMAIL); // default
    }

    @Test
    void updateExistingPreference() {
        UserPreference initial = new UserPreference("user123", Set.of(ChannelType.EMAIL));
        preferenceService.savePreference(initial);

        UserPreference updated = new UserPreference("user123", Set.of(ChannelType.SMS, ChannelType.PUSH));
        preferenceService.savePreference(updated);

        UserPreference retrieved = preferenceService.getPreference("user123");
        assertThat(retrieved.getPreferredChannels()).containsExactlyInAnyOrder(ChannelType.SMS, ChannelType.PUSH);
    }
}