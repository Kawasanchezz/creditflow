package com.creditflow.policy;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("creditflow.policy")
public record PolicyProperties(BigDecimal minIncome, BigDecimal maxCommitmentRatio,
                               int minRelationshipMonths, int minScore) {
}
