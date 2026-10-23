package com.hotel.system.service;

import com.hotel.system.model.Notification;
import com.hotel.system.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;

    public Notification sendNotification(
            String recipientEmail,
            String recipientName,
            String title,
            String message,
            String type,
            Long referenceId
    ) {
        Notification notification = Notification.builder()
                .recipientEmail(recipientEmail)
                .recipientName(recipientName)
                .title(title)
                .message(message)
                .type(type)
                .referenceId(referenceId)
                .isRead(false)
                .deliveryChannel("IN_APP")
                .deliveryStatus("DELIVERED")
                .createdAt(LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        logger.info("Delivered in-app notification [{}] to [{}]", type, recipientEmail);
        return saved;
    }

    public List<Notification> getNotificationsForUser(String email) {
        return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email);
    }

    public boolean markAsRead(String notificationId, String requesterEmail) {
        return notificationRepository.findById(notificationId).map(n -> {
            if (requesterEmail != null && !requesterEmail.equalsIgnoreCase(n.getRecipientEmail())) {
                logger.warn("User [{}] attempted to mark notification [{}] belonging to [{}] as read",
                        requesterEmail, notificationId, n.getRecipientEmail());
                return false;
            }
            n.setRead(true);
            notificationRepository.save(n);
            return true;
        }).orElse(false);
    }

    public void markAsRead(String notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    public void markAllAsRead(String email) {
        List<Notification> unread = notificationRepository.findByRecipientEmailAndIsReadFalseOrderByCreatedAtDesc(email);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}
