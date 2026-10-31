package com.notificationsystem.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelTypeTest {

    @Test
    void channelTypesExist() {
        assertThat(ChannelType.EMAIL).isNotNull();
        assertThat(ChannelType.SMS).isNotNull();
        assertThat(ChannelType.PUSH).isNotNull();
        assertThat(ChannelType.WHATSAPP).isNotNull();
        assertThat(ChannelType.VOICE).isNotNull();
    }

    @Test
    void channelTypeValues() {
        assertThat(ChannelType.EMAIL.name()).isEqualTo("EMAIL");
        assertThat(ChannelType.SMS.name()).isEqualTo("SMS");
        assertThat(ChannelType.PUSH.name()).isEqualTo("PUSH");
        assertThat(ChannelType.WHATSAPP.name()).isEqualTo("WHATSAPP");
        assertThat(ChannelType.VOICE.name()).isEqualTo("VOICE");
    }
}