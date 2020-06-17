package org.c19x.logic;

import org.c19x.beacon.ConcreteBeaconCodes;
import org.c19x.data.Logger;
import org.c19x.data.Settings;
import org.c19x.data.primitive.QuadConsumer;
import org.c19x.data.primitive.Triple;
import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.Advice;
import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.Contact;
import org.c19x.data.type.ExposureOverTime;
import org.c19x.data.type.ExposurePeriod;
import org.c19x.data.type.ExposureProximity;
import org.c19x.data.type.InfectionData;
import org.c19x.data.type.RSSI;
import org.c19x.data.type.Status;
import org.c19x.data.type.Time;
import org.c19x.data.type.TimeInterval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class ConcreteRiskAnalysis implements RiskAnalysis {
    private final static String tag = ConcreteRiskAnalysis.class.getName();

    @Override
    public void advice(Deque<Contact> contacts, Settings settings, QuadConsumer<Advice, Status, ExposureOverTime, ExposureProximity> callback) {
        final Triple<Status, Time, Time> settingsStatus = settings.status();
        final Triple<Advice, Advice, Time> settingsAdvice = settings.advice();
        final ExposurePeriod exposureThreshold = settings.exposure();
        // Match in background
        final Thread thread = new Thread(() -> {
            final Triple<ExposurePeriod, ExposureOverTime, ExposureProximity> match = match(contacts, settings);
            final Advice advice = (settingsStatus.a != Status.healthy ? Advice.selfIsolation :
                    (match.a.value < exposureThreshold.value ? settingsAdvice.a : Advice.selfIsolation));
            final Status contactStatus = (match.a.value == 0 ? Status.healthy : Status.infectious);
            Logger.debug(tag, "Advice (advice={},default={},status={},contactStatus={},exposure={},proximity={})",
                    advice, settingsAdvice.a, settingsStatus.a, contactStatus, match.a, match.c);
            callback.accept(advice, contactStatus, match.b, match.c);
        });
        thread.start();
    }

    /**
     * Match contacts against infection data.
     */
    private Triple<ExposurePeriod, ExposureOverTime, ExposureProximity> match(Deque<Contact> contacts, Settings settings) {
        final Tuple<InfectionData, Time> infectionData = settings.infectionData();
        final RSSI rssiThreshold = settings.proximity();
        final Map<BeaconCode, Collection<Contact>> beaconsForMatching = beacons(contacts);
        final ExposureOverTime exposureOverTime = exposure(beaconsForMatching, infectionData.a);
        final ExposureProximity exposureProximity = proximity(exposureOverTime);
        final ExposurePeriod exposurePeriod = period(exposureProximity, rssiThreshold);
        return new Triple<>(exposurePeriod, exposureOverTime, exposureProximity);
    }

    /**
     * Create map of beacon codes for matching.
     */
    private Map<BeaconCode, Collection<Contact>> beacons(Deque<Contact> contacts) {
        final Map<BeaconCode, Collection<Contact>> beacons = new HashMap<>();
        contacts.forEach(contact -> {
            Collection<Contact> beaconCodeContacts = beacons.get(contact.code);
            if (beaconCodeContacts == null) {
                beaconCodeContacts = new ArrayList<>(1);
                beacons.put(contact.code, beaconCodeContacts);
            }
            beaconCodeContacts.add(contact);
        });
        return beacons;
    }

    /**
     * Regenerate beacon codes from infection data for matching to establish exposure over time.
     */
    private ExposureOverTime exposure(Map<BeaconCode, Collection<Contact>> beacons, InfectionData infectionData) {
        final ExposureOverTime exposureOverTime = new ExposureOverTime();
        infectionData.value.forEach((beaconCodeSeed, status) -> {
            if (status == Status.healthy) {
                // Matching symptomatic or confirmed diagnosis only
                return;
            }
            // Regenerate beacon codes based on seed
            final BeaconCode[] beaconCodesForMatching = ConcreteBeaconCodes.beaconCodes(beaconCodeSeed, ConcreteBeaconCodes.codesPerDay);
            for (int i = 0; i < beaconCodesForMatching.length; i++) {
                final BeaconCode beaconCode = beaconCodesForMatching[i];
                final Collection<Contact> contacts = beacons.get(beaconCode);
                if (contacts == null) {
                    // Unmatched
                    continue;
                }
                contacts.forEach(contact -> {
                    final ExposurePeriod exposurePeriod = new ExposurePeriod((int) (contact.time.timeIntervalSinceNow().value / TimeInterval.minute.value));
                    // Identify nearest encounter for each exposure period
                    final RSSI exposureProximity = exposureOverTime.value.get(exposurePeriod);
                    if (exposureProximity == null || exposureProximity.value < contact.rssi.value) {
                        exposureOverTime.value.put(exposurePeriod, contact.rssi);
                    }
                });
            }
        });
        return exposureOverTime;
    }

    /**
     * Histogram of exposure proximity
     */
    private ExposureProximity proximity(ExposureOverTime exposureOverTime) {
        final ExposureProximity proximity = new ExposureProximity();
        exposureOverTime.value.values().forEach(rssi -> {
            final Integer count = proximity.value.get(rssi);
            if (count == null) {
                proximity.value.put(rssi, 1);
            } else {
                proximity.value.put(rssi, 1 + count.intValue());
            }
        });
        return proximity;
    }

    /**
     * Calculate exposure period.
     */
    private ExposurePeriod period(ExposureProximity proximity, RSSI threshold) {
        final ExposurePeriod period = new ExposurePeriod(0);
        proximity.value.forEach((rssi, count) -> {
            if (rssi.value >= threshold.value) {
                period.value += count.intValue();
            }
        });
        return period;
    }

}
