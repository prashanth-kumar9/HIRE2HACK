//package com.example.hackathon.hackathon.service;
//
//import com.example.hackathon.hackathon.entity.Application;
//import com.example.hackathon.hackathon.repository.ApplicationRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.time.LocalDateTime;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyLong;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class NotificationServiceTest {
//
//    @Mock
//    private SmsService smsService;
//
//    @Mock
//    private EmailService emailService;
//
//    @Mock
//    private NotificationTemplateService templateService;
//
//    @Mock
//    private ApplicationRepository applicationRepository;
//
//    @InjectMocks
//    private NotificationService notificationService;
//
//    private Application application;
//
//    @BeforeEach
//    void setUp() {
//        application = Application.builder()
//                .id(1L)
//                .candidateName("John Doe")
//                .email("john@example.com")
//                .phoneNumber("+1234567890")
//                .position("Software Engineer")
//                .selected(true)
//                .selectedAt(LocalDateTime.now())
//                .smsSent(false)
//                .emailSent24Hrs(false)
//                .emailSent48Hrs(false)
//                .build();
//    }
//
//    @Test
//    void sendSmsNotification_Success() {
//        when(templateService.getSmsTemplate()).thenReturn("Test SMS");
//        when(smsService.sendSms(any(Application.class), anyString()))
//                .thenReturn(new SmsService.SmsResponse(true, "Success"));
//        when(applicationRepository.markSmsAsSent(anyLong())).thenReturn(1);
//
//        boolean result = notificationService.sendSmsNotification(application);
//
//        assertTrue(result);
//        verify(applicationRepository, times(1)).markSmsAsSent(1L);
//    }
//
//    @Test
//    void sendSmsNotification_Failure_SmsServiceFails() {
//        when(templateService.getSmsTemplate()).thenReturn("Test SMS");
//        when(smsService.sendSms(any(Application.class), anyString()))
//                .thenReturn(new SmsService.SmsResponse(false, "Failed"));
//
//        boolean result = notificationService.sendSmsNotification(application);
//
//        assertFalse(result);
//        verify(applicationRepository, never()).markSmsAsSent(anyLong());
//    }
//
//    @Test
//    void sendSmsNotification_Failure_DatabaseUpdateFails() {
//        when(templateService.getSmsTemplate()).thenReturn("Test SMS");
//        when(smsService.sendSms(any(Application.class), anyString()))
//                .thenReturn(new SmsService.SmsResponse(true, "Success"));
//        when(applicationRepository.markSmsAsSent(anyLong())).thenReturn(0);
//
//        boolean result = notificationService.sendSmsNotification(application);
//
//        assertFalse(result);
//    }
//
//    @Test
//    void send24HrEmailNotification_Success() {
//        when(templateService.getEmail24HrsSubject()).thenReturn("Subject");
//        when(templateService.getEmail24HrsBody()).thenReturn("Body");
//        when(emailService.sendEmail(any(Application.class), anyString(), anyString()))
//                .thenReturn(new EmailService.EmailResponse(true, "Success"));
//        when(applicationRepository.markEmail24HrsAsSent(anyLong())).thenReturn(1);
//
//        boolean result = notificationService.send24HrEmailNotification(application);
//
//        assertTrue(result);
//        verify(applicationRepository, times(1)).markEmail24HrsAsSent(1L);
//    }
//
//    @Test
//    void send24HrEmailNotification_Failure_EmailServiceFails() {
//        when(templateService.getEmail24HrsSubject()).thenReturn("Subject");
//        when(templateService.getEmail24HrsBody()).thenReturn("Body");
//        when(emailService.sendEmail(any(Application.class), anyString(), anyString()))
//                .thenReturn(new EmailService.EmailResponse(false, "Failed"));
//
//        boolean result = notificationService.send24HrEmailNotification(application);
//
//        assertFalse(result);
//        verify(applicationRepository, never()).markEmail24HrsAsSent(anyLong());
//    }
//
//    @Test
//    void send48HrEmailNotification_Success() {
//        when(templateService.getEmail48HrsSubject()).thenReturn("Subject");
//        when(templateService.getEmail48HrsBody()).thenReturn("Body");
//        when(emailService.sendEmail(any(Application.class), anyString(), anyString()))
//                .thenReturn(new EmailService.EmailResponse(true, "Success"));
//        when(applicationRepository.markEmail48HrsAsSent(anyLong())).thenReturn(1);
//
//        boolean result = notificationService.send48HrEmailNotification(application);
//
//        assertTrue(result);
//        verify(applicationRepository, times(1)).markEmail48HrsAsSent(1L);
//    }
//
//    @Test
//    void send48HrEmailNotification_Failure_EmailServiceFails() {
//        when(templateService.getEmail48HrsSubject()).thenReturn("Subject");
//        when(templateService.getEmail48HrsBody()).thenReturn("Body");
//        when(emailService.sendEmail(any(Application.class), anyString(), anyString()))
//                .thenReturn(new EmailService.EmailResponse(false, "Failed"));
//
//        boolean result = notificationService.send48HrEmailNotification(application);
//
//        assertFalse(result);
//        verify(applicationRepository, never()).markEmail48HrsAsSent(anyLong());
//    }
//}
