package edu.example.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class CourseDeliveryApplication {
    private final CourseDeliveryService deliveries;

    public CourseDeliveryApplication(CourseDeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    public static void main(String[] args) {
        SpringApplication.run(CourseDeliveryApplication.class, args);
    }

    @PostMapping("/course-deliveries")
    public CourseDeliveryService.Report deliver(@RequestBody CourseDeliveryService.Attempt attempt) {
        return deliveries.evaluate(attempt);
    }

    @ExceptionHandler(InfraiErrorClient.ApiException.class)
    public ResponseEntity<Map<String, String>> upstream(InfraiErrorClient.ApiException error) {
        HttpStatus status = error.status() >= 400 && error.status() < 500
                ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("error", error.code()));
    }
}
