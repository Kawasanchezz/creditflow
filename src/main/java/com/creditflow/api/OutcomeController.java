package com.creditflow.api;

import com.creditflow.outcome.OutcomeService;
import com.creditflow.outcome.OutcomeService.OutcomeView;
import com.creditflow.outcome.OutcomeService.SummaryRow;
import com.creditflow.outcome.OutcomeType;
import com.creditflow.security.AccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class OutcomeController {
    public record OutcomeRequest(@NotNull OutcomeType type, @Size(max = 400) @Pattern(regexp = "[^\\x00]*", message = "caracteres de controle nao permitidos") String detail, @NotNull LocalDate occurredOn) {
    }

    private final OutcomeService outcomes;
    private final AccessGuard guard;

    public OutcomeController(OutcomeService outcomes, AccessGuard guard) {
        this.outcomes = outcomes;
        this.guard = guard;
    }

    /** Somente analistas (regra na SecurityConfig). */
    @PostMapping("/api/proposals/{id}/outcomes")
    public ResponseEntity<OutcomeView> record(@PathVariable Long id, @Valid @RequestBody OutcomeRequest r, Authentication auth) {
        OutcomeView v = outcomes.record(id, r.type(), r.detail(), r.occurredOn(), auth.getName());
        return ResponseEntity.created(URI.create("/api/proposals/" + id + "/outcomes")).body(v);
    }

    @GetMapping("/api/proposals/{id}/outcomes")
    public List<OutcomeView> list(@PathVariable Long id, Authentication auth) {
        guard.checkProposal(auth, id);
        return outcomes.list(id);
    }

    /** Somente analistas (regra na SecurityConfig). */
    @GetMapping("/api/outcomes/summary")
    public List<SummaryRow> summary() {
        return outcomes.summary();
    }
}
