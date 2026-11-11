package com.creditflow.api;

import com.creditflow.api.ApiDtos.InstallmentResponse;
import com.creditflow.installment.InstallmentService;
import com.creditflow.security.AccessGuard;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final InstallmentService installments;
    private final AccessGuard guard;

    public ContractController(InstallmentService installments, AccessGuard guard) {
        this.installments = installments;
        this.guard = guard;
    }

    @GetMapping("/{id}/installments")
    public List<InstallmentResponse> list(@PathVariable Long id, Authentication auth) {
        guard.checkContract(auth, id);
        return installments.list(id).stream().map(InstallmentResponse::from).toList();
    }

    @PostMapping("/{id}/installments/{number}/prepay")
    public InstallmentResponse prepay(@PathVariable Long id, @PathVariable int number, Authentication auth) {
        guard.checkContract(auth, id);
        return InstallmentResponse.from(installments.prepay(id, number));
    }
}
