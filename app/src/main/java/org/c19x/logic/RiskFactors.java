package org.c19x.logic;

import org.c19x.data.HealthStatus;

/**
 * Risk factors for consideration in making a recommendation.
 */
public final class RiskFactors {
    public byte healthStatus = HealthStatus.NO_SYMPTOM;
    public byte governmentAdvice = HealthStatus.STAY_AT_HOME;
    public long closeContactDuration = 0;
    public long closeContactWithInfectiousDuration = 0;

    @Override
    public String toString() {
        return "RiskFactors{" +
                "healthStatus=" + healthStatus +
                ", governmentAdvice=" + governmentAdvice +
                ", closeContactDuration=" + closeContactDuration +
                ", closeContactWithInfectiousDuration=" + closeContactWithInfectiousDuration +
                '}';
    }
}

