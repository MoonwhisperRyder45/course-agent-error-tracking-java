package edu.example.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CourseDeliveryServiceTest {
    @Test
    void missedDeadlineAndFailedAgentReachEducator() {
        InfraiErrorClient errors = mock(InfraiErrorClient.class);
        CourseDeliveryService service = new CourseDeliveryService(errors);
        Instant deadline = Instant.parse("2026-09-20T10:00:00Z");
        var report = service.evaluate(new CourseDeliveryService.Attempt(
                "attempt-42", "algebra-1", "learner-7", deadline, deadline.plusSeconds(60), false));
        assertEquals("NEEDS_EDUCATOR_REVIEW", report.deliveryStatus());
        assertTrue(report.educatorReview());
        verify(errors).capture(eq("attempt-42"), anyMap());
    }

    @Test
    void onTimeDeliveryDoesNotCreateAnError() {
        InfraiErrorClient errors = mock(InfraiErrorClient.class);
        Instant deadline = Instant.parse("2026-09-20T10:00:00Z");
        var report = new CourseDeliveryService(errors).evaluate(new CourseDeliveryService.Attempt(
                "attempt-43", "algebra-1", "learner-7", deadline, deadline, true));
        assertEquals("DELIVERED", report.deliveryStatus());
        assertFalse(report.educatorReview());
        verifyNoInteractions(errors);
    }
}
