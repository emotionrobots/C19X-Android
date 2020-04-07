package org.c19x.logic;

/**
 * Listener for risk analysis updates.
 */
public interface RiskAnalysisListener {

    /**
     * Update to contact status and overall advice.
     *
     * @param contact Contact status.
     * @param advice  Action advice.
     */
    void update(final byte contact, final byte advice);
}
