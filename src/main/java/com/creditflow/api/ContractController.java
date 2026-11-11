package com.creditflow.api;

import com.creditflow.api.ApiDtos.InstallmentResponse;
import com.creditflow.installment.InstallmentService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final InstallmentService installments;

    public ContractController(InstallmentService installments) {
        this.installments = installments;
    }

    @GetMapping("/{id}/installments")
    public List<InstallmentResponse> list(@PathVariable Long id) {
        return installments.list(id).stream().map(InstallmentResponse::from).toList();
    }

    /** Antecipacao com desconto de juros (opcional no MVP). */
    @PostMapping("/{id}/installments/{number}/prepay")
    public InstallmentResponse prepay(@PathVariable Long id, @PathVariable int number) {
        return InstallmentResponse.from(installments.prepay(id, number));
    }
}
