package com.siktak.feedback.api;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.feedback.application.FeedbackService;
import com.siktak.feedback.domain.Feedback;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/feedbacks")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse submit(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody FeedbackRequest request) {
        return FeedbackResponse.from(feedbackService.submit(user.id(), request.applicationId(),
                request.tableRating(), request.conversationRating(), request.venueRating(), request.comment()));
    }

    @GetMapping("/me/feedbacks")
    public List<FeedbackResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return feedbackService.findMine(user.id()).stream().map(FeedbackResponse::from).toList();
    }

    public record FeedbackRequest(
            @NotNull Long applicationId,
            @NotNull @Min(1) @Max(5) Integer tableRating,
            @NotNull @Min(1) @Max(5) Integer conversationRating,
            @NotNull @Min(1) @Max(5) Integer venueRating,
            @Size(max = 1000) String comment) {
    }

    public record FeedbackResponse(Long id, Long applicationId, int tableRating, int conversationRating,
            int venueRating, String comment, Instant submittedAt) {

        static FeedbackResponse from(Feedback f) {
            return new FeedbackResponse(f.getId(), f.getApplicationId(), f.getTableRating(),
                    f.getConversationRating(), f.getVenueRating(), f.getComment(), f.getSubmittedAt());
        }
    }
}
