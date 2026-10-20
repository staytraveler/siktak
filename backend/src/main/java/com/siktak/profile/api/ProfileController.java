package com.siktak.profile.api;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.profile.application.ContactService;
import com.siktak.profile.application.ContactService.Contact;
import com.siktak.profile.application.ProfileService;
import com.siktak.profile.application.ProfileService.ProfileInput;
import com.siktak.profile.domain.Profile;

import lombok.RequiredArgsConstructor;

/** 내 프로필 조회·저장 (ACC-02~05). 본인 것만 다룬다. */
@RestController
@RequestMapping("/api/v1/me/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final ContactService contactService;

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal AuthUser user) {
        return ProfileResponse.from(profileService.get(user.id()), contactService.get(user.id()));
    }

    @PutMapping
    public ProfileResponse save(@AuthenticationPrincipal AuthUser user, @RequestBody ProfileRequest request) {
        Contact contact = contactService.save(user.id(), request.name(), request.phone());
        return ProfileResponse.from(profileService.save(user.id(), request.toInput()), contact);
    }

    public record ProfileRequest(String name, String phone, Integer birthYear, String gender, String job, String mbti,
            List<String> interests, List<String> selfTraits, List<String> preferredTraits, String conversationStyle,
            String priceRange, String foodRestrictions, String allergies, boolean privacyAgreed,
            boolean policyAgreed) {

        ProfileInput toInput() {
            return new ProfileInput(birthYear, gender, job, mbti, interests, selfTraits, preferredTraits,
                    conversationStyle, priceRange, foodRestrictions, allergies, privacyAgreed, policyAgreed);
        }
    }

    public record ProfileResponse(boolean complete, String name, String phone, Integer birthYear, String gender, String job, String mbti,
            List<String> interests, List<String> selfTraits, List<String> preferredTraits, String conversationStyle,
            String priceRange, String foodRestrictions, String allergies, Instant privacyAgreedAt,
            Instant policyAgreedAt) {

        static ProfileResponse from(Profile p, Contact c) {
            return new ProfileResponse(p.isComplete(), c.name(), c.phone(), p.getBirthYear(), p.getGender(), p.getJob(), p.getMbti(),
                    p.getInterests(), p.getSelfTraits(), p.getPreferredTraits(), p.getConversationStyle(),
                    p.getPriceRange(), p.getFoodRestrictions(), p.getAllergies(), p.getPrivacyAgreedAt(),
                    p.getPolicyAgreedAt());
        }
    }
}
