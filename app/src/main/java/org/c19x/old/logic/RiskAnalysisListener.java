package org.c19x.old.logic;

/**
 * Listener for risk analysis updates.
 */
public interface RiskAnalysisListener {

    /**
     * Update to contact status and overall advice.
     *
     * @param riskFactors Risk factor data.
     * @param contact Contact status.
     * @param advice  Action advice.
     */
    void update(final RiskFactors riskFactors, final byte contact, final byte advice);
}
