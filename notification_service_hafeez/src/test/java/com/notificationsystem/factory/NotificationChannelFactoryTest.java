package com.notificationsystem.factory;

import com.notificationsystem.channel.EmailNotificationChannel;
import com.notificationsystem.channel.NotificationChannel;
import com.notificationsystem.channel.PushNotificationChannel;
import com.notificationsystem.channel.SmsNotificationChannel;
import com.notificationsystem.model.ChannelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationChannelFactoryTest {

    @Mock
    private EmailNotificationChannel emailChannel;

    @Mock
    private SmsNotificationChannel smsChannel;

    @Mock
    private PushNotificationChannel pushChannel;

    @InjectMocks
    private NotificationChannelFactory factory;

    @Test
    void getChannelReturnsEmailChannel() {
        NotificationChannel channel = factory.getChannel(ChannelType.EMAIL);
        assertThat(channel).isSameAs(emailChannel);
    }

    @Test
    void getChannelReturnsSmsChannel() {
        NotificationChannel channel = factory.getChannel(ChannelType.SMS);
        assertThat(channel).isSameAs(smsChannel);
    }

    @Test
    void getChannelReturnsPushChannel() {
        NotificationChannel channel = factory.getChannel(ChannelType.PUSH);
        assertThat(channel).isSameAs(pushChannel);
    }

    @Test
    void getChannelThrowsForUnsupportedChannel() {
        // Since the factory only initializes EMAIL, SMS, PUSH
        // Unsupported channels like WHATSAPP, VOICE will throw
        assertThatThrownBy(() -> factory.getChannel(ChannelType.WHATSAPP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported channel type");
    }
}