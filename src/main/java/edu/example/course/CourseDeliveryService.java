package edu.example.course;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CourseDeliveryService {
    private final InfraiErrorClient errors;

    public CourseDeliveryService(InfraiErrorClient errors) {
        this.errors = errors;
    }

    public record Attempt(String attemptId, String courseId, String learnerId,
                          Instant deadline, Instant submittedAt, boolean agentSucceeded) {}
    public record Report(String attemptId, String courseId, String learnerId,
                         String deliveryStatus, boolean educatorReview) {}

    public Report evaluate(Attempt attempt) {
        if (attempt.attemptId() == null || attempt.attemptId().isBlank()
                || attempt.courseId() == null || attempt.courseId().isBlank()
                || attempt.learnerId() == null || attempt.learnerId().isBlank()
                || attempt.deadline() == null || attempt.submittedAt() == null) {
            throw new IllegalArgumentException("An attempt needs identifiers, a deadline and a submission time");
        }
        boolean late = attempt.submittedAt().isAfter(attempt.deadline());
        if (!attempt.agentSucceeded()) {
            errors.capture(attempt.attemptId(), Map.of(
                    "title", "Course delivery agent failed",
                    "message", "Learner delivery needs educator review",
                    "level", "error",
                    "fingerprint", List.of("course-delivery", attempt.courseId()),
                    "exception", "CourseDeliveryException: agent could not complete delivery",
                    "context", Map.of("courseId", attempt.courseId(), "learnerId", attempt.learnerId(),
                            "attemptId", attempt.attemptId(), "deadline", attempt.deadline().toString())));
        }
        boolean review = late || !attempt.agentSucceeded();
        return new Report(attempt.attemptId(), attempt.courseId(), attempt.learnerId(),
                review ? "NEEDS_EDUCATOR_REVIEW" : "DELIVERED", review);
    }
}
