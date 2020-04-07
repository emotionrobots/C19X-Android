package org.c19x.data;

import org.c19x.util.messaging.DefaultBroadcaster;

/**
 * Health status of an individual
 */
public final class HealthStatus extends DefaultBroadcaster<HealthStatusListener> {
    private final static String tag = HealthStatus.class.getName();

    // Health status of an individual
    /**
     * No obvious symptom
     */
    public final static byte NO_SYMPTOM = (byte) 0;

    /**
     * Potentially infected via close contact with symptomatic / confirmed cases
     */
    public final static byte POTENTIALLY_INFECTED = (byte) 1;

    /**
     * Self-reported to be exhibiting COVID-19 symptoms
     */
    public final static byte HAS_SYMPTOM = (byte) 2;

    /**
     * Confirmed diagnosis of COVID-19
     */
    public final static byte CONFIRMED_DIAGNOSIS = (byte) 3;

    // Health status of close contacts
    /**
     * Nothing reported
     */
    public final static byte NO_REPORT = (byte) 10;

    /**
     * Infectious, or potentially infectious
     */
    public final static byte INFECTIOUS = (byte) 11;


    // Action advice
    /**
     * No restriction of movement or contact, back to normality
     */
    public final static byte NO_RESTRICTION = (byte) 20;

    /**
     * Stay at home
     */
    public final static byte STAY_AT_HOME = (byte) 22;

    /**
     * Self-isolation
     */
    public final static byte SELF_ISOLATION = (byte) 23;

    /**
     * Health status
     */
    private byte status = NO_SYMPTOM;

    /**
     * Get current health status.
     *
     * @return
     */
    public synchronized byte getStatus() {
        return status;
    }

    /**
     * Set current health status.
     *
     * @param status
     */
    public synchronized void setStatus(byte status) {
        final byte currentStatus = this.status;
        if (currentStatus != status) {
            this.status = status;
            broadcast(l -> l.update(currentStatus, status));
        }
    }

    /**
     * Get string representation of health status for logging.
     *
     * @return
     */
    @Override
    public String toString() {
        return toString(getStatus());
    }

    /**
     * Get string representation of health status for logging.
     *
     * @param status
     * @return
     */
    public final static String toString(final byte status) {
        switch (status) {
            case NO_SYMPTOM:
                return "NO_SYMPTOM";
            case POTENTIALLY_INFECTED:
                return "POTENTIALLY_INFECTED";
            case HAS_SYMPTOM:
                return "HAS_SYMPTOM";
            case CONFIRMED_DIAGNOSIS:
                return "CONFIRMED_DIAGNOSIS";
            case NO_REPORT:
                return "NO_REPORT";
            case INFECTIOUS:
                return "INFECTIOUS";
            case NO_RESTRICTION:
                return "NO_RESTRICTION";
            case STAY_AT_HOME:
                return "STAY_AT_HOME";
            case SELF_ISOLATION:
                return "SELF_ISOLATION";
            default:
                return "UNKNOWN";
        }
    }
}
