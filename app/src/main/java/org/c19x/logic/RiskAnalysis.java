package org.c19x.logic;

import android.util.LongSparseArray;

import org.c19x.C19XApplication;
import org.c19x.data.GlobalStatusLog;
import org.c19x.data.HealthStatus;
import org.c19x.data.primitive.MutableLong;
import org.c19x.util.Logger;
import org.c19x.util.messaging.DefaultBroadcaster;

/**
 * Analysis function for assessing risk of infection and making recommendation for action.
 */
public class RiskAnalysis extends DefaultBroadcaster<RiskAnalysisListener> {
    private final static String tag = RiskAnalysis.class.getName();

    /**
     * Risk factors for consideration in making a recommendation.
     */
    private final static class RiskFactors {
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


    /**
     * Update risk assessment and recommendation according to latest information.
     */
    public void updateAssessment() {
        final GlobalStatusLog globalStatusLog = C19XApplication.getGlobalStatusLog();
        final LongSparseArray<MutableLong> closeContacts = C19XApplication.getDetectionEventLog().getContacts();

        final RiskFactors riskFactors = getRiskFactors(globalStatusLog, closeContacts);
        final byte advice = getAdvice(globalStatusLog, riskFactors);
        final byte contact = getContact(globalStatusLog, riskFactors);

        Logger.info(tag, "Risk analysis (contact={}({}),advice={}({}),riskFactors={})", HealthStatus.toString(contact), contact, HealthStatus.toString(advice), advice, riskFactors);

        broadcast(l -> l.update(contact, advice));
    }

    /**
     * Compute risk factors based on global status log parameters and close contact data.
     *
     * @param globalStatusLog
     * @param closeContacts
     * @return Summary of risk factors for analysis.
     */
    private final static RiskFactors getRiskFactors(final GlobalStatusLog globalStatusLog, final LongSparseArray<MutableLong> closeContacts) {
        final RiskFactors riskFactors = new RiskFactors();
        riskFactors.healthStatus = C19XApplication.getHealthStatus().getStatus();
        riskFactors.governmentAdvice = globalStatusLog.getGovernmentAdvice();

        for (int i = closeContacts.size(); i-- > 0; ) {
            final long id = closeContacts.keyAt(i);
            final long duration = closeContacts.valueAt(i).value;
            final boolean infectious = globalStatusLog.getInfectious((int) (id % C19XApplication.anonymousIdRange));
            Logger.debug(tag, "Contact (id={},duration={},infectious={})", id, duration, infectious);
            riskFactors.closeContactDuration += duration;
            if (infectious) {
                riskFactors.closeContactWithInfectiousDuration += duration;
            }
        }
        return riskFactors;
    }

    /**
     * Compute close contact status based on global parameters and risk factors
     *
     * @param g Global status log to provide latest parameters and recommendations.
     * @param r Personal risk factors computed from health status of the individual and close contact with other people.
     * @return
     */
    private final static byte getContact(final GlobalStatusLog g, final RiskFactors r) {
        // Default is no report
        byte contact = HealthStatus.NO_REPORT;

        // If a close contact is infectious, you need to be aware
        if (r.closeContactWithInfectiousDuration > 0) {
            contact = HealthStatus.INFECTIOUS;
        }

        return contact;
    }

    /**
     * Compute advice based on global parameters and personal risk factors
     *
     * @param g Global status log to provide latest parameters and recommendations.
     * @param r Personal risk factors computed from health status of the individual and close contact with other people.
     * @return
     */
    private final static byte getAdvice(final GlobalStatusLog g, final RiskFactors r) {
        // Default government advice
        byte advice = r.governmentAdvice;

        // If you have symptom or confirmed diagnosis, you should self-isolate
        if (r.healthStatus == HealthStatus.HAS_SYMPTOM || r.healthStatus == HealthStatus.CONFIRMED_DIAGNOSIS) {
            advice = (advice > HealthStatus.SELF_ISOLATION ? advice : HealthStatus.SELF_ISOLATION);
        }

        // If duration of close contact with infectious people exceeds thresholds, you should self-isolate
        if (r.closeContactWithInfectiousDuration >= g.getExposureDurationThreshold()) {
            advice = (advice > HealthStatus.SELF_ISOLATION ? advice : HealthStatus.SELF_ISOLATION);
        }

        return advice;
    }
}
