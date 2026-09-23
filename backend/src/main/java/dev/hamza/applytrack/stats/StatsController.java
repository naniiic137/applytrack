package dev.hamza.applytrack.stats;

import dev.hamza.applytrack.auth.AuthUser;
import dev.hamza.applytrack.common.ClientTimeZone;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Stats", description = "Dashboard statistics")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public StatsResponse stats(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "IANA time zone of the user, e.g. Africa/Tunis. Decides what 'today' is for "
                    + "overdue follow-ups and weekly buckets. Missing or unknown: UTC.")
            @RequestHeader(name = ClientTimeZone.HEADER, required = false) String timeZone) {
        return statsService.forUser(user.id(), ClientTimeZone.resolve(timeZone));
    }
}
