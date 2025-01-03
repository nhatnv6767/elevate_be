package com.elevatebanking.service.notification;

import com.elevatebanking.entity.notification.NotificationChannel;
import com.elevatebanking.entity.notification.NotificationPreference;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.NotificationChannelRepository;
import com.elevatebanking.repository.NotificationPreferenceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationPreferenceService {
    NotificationPreferenceRepository preferenceRepository;
    NotificationChannelRepository channelRepository;

    @Transactional(readOnly = true)
    public NotificationPreference getUserPreferences(String userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreference(userId));
    }

    @Transactional
    public NotificationPreference updatePreferences(String userId, NotificationPreference preference) {
        NotificationPreference existing = getUserPreferences(userId);
        updatePreferenceFields(existing, preference);
        return preferenceRepository.save(existing);
    }

    @Transactional
    public void enableChannel(String userId, String channelCode) {
        NotificationPreference preferences = getUserPreferences(userId);
        NotificationChannel channel = channelRepository.findByChannelCode(channelCode)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found: " + channelCode));
        // TODO: Check if channel is active
        preferences.getEnabledChannels().add(channel);
        preferenceRepository.save(preferences);
    }

    @Transactional
    public void disableChannel(String userId, String channelCode) {
        NotificationPreference preferences = getUserPreferences(userId);
        NotificationChannel channel = channelRepository.findByChannelCode(channelCode)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found: " + channelCode));
        // TODO: Check if channel is active
        preferences.getEnabledChannels().remove(channel);
        preferenceRepository.save(preferences);
    }

    NotificationPreference createDefaultPreference(String userId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setId(userId);
        preference.setEmailEnabled(true);
        preference.setPushEnabled(true);
        preference.setSmsEnabled(false);
        return preferenceRepository.save(preference);
    }

    void updatePreferenceFields(NotificationPreference existing, NotificationPreference updated) {
        existing.setEmailEnabled(updated.isEmailEnabled());
        existing.setPushEnabled(updated.isPushEnabled());
        existing.setSmsEnabled(updated.isSmsEnabled());
        existing.setEnabledTypes(updated.getEnabledTypes());
        existing.setEnabledChannels(updated.getEnabledChannels());
//        preferenceRepository.save(existing);
    }
}
