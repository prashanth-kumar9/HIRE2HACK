package com.notificationsystem.template;

import com.notificationsystem.model.ScreeningNotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateFactoryTest {

    @Mock
    private EmailTemplate emailTemplate;

    @Mock
    private SmsTemplate smsTemplate;

    @InjectMocks
    private NotificationTemplateFactory factory;

    @Test
    void getTemplateReturnsEmailTemplate() {
        var template = factory.getTemplate("EMAIL");

        assertThat(template).isSameAs(emailTemplate);
    }

    @Test
    void getTemplateReturnsSmsTemplate() {
        var template = factory.getTemplate("SMS");

        assertThat(template).isSameAs(smsTemplate);
    }

    @Test
    void getTemplateCaseInsensitive() {
        var template1 = factory.getTemplate("email");
        var template2 = factory.getTemplate("Email");
        var template3 = factory.getTemplate("EMAIL");

        assertThat(template1).isSameAs(template2).isSameAs(template3);
    }

    @Test
    void getTemplateThrowsForUnsupportedChannel() {
        assertThatThrownBy(() -> factory.getTemplate("WHATSAPP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported channel");
    }

    @Test
    void getEmailTemplateReturnsEmailTemplate() {
        assertThat(factory.getEmailTemplate()).isSameAs(emailTemplate);
    }

    @Test
    void getSmsTemplateReturnsSmsTemplate() {
        assertThat(factory.getSmsTemplate()).isSameAs(smsTemplate);
    }
}