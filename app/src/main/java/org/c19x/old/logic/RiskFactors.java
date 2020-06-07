package org.c19x.old.logic;

import org.c19x.old.data.HealthStatus;

/**
 * Risk factors for consideration in making a recommendation.
 */
public final class RiskFactors {
    public byte healthStatus = HealthStatus.NO_SYMPTOM;
    public byte governmentAdvice = HealthStatus.STAY_AT_HOME;
    public int detectionDays = 0;
    public long closeContactDuration = 0;
    public long closeContactWithInfectiousDuration = 0;

    @Override
    public String toString() {
        return "RiskFactors{" +
                "healthStatus=" + healthStatus +
                ", governmentAdvice=" + governmentAdvice +
                ", detectionDays=" + detectionDays +
                ", closeContactDuration=" + closeContactDuration +
                ", closeContactWithInfectiousDuration=" + closeContactWithInfectiousDuration +
                '}';
    }
}

