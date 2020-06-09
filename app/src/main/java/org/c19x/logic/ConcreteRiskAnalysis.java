package org.c19x.logic;

import org.c19x.data.Settings;
import org.c19x.data.primitive.QuadConsumer;
import org.c19x.data.type.Advice;
import org.c19x.data.type.Contact;
import org.c19x.data.type.ExposureOverTime;
import org.c19x.data.type.ExposureProximity;
import org.c19x.data.type.Status;

import java.util.Deque;

public class ConcreteRiskAnalysis implements RiskAnalysis {
    @Override
    public void advice(Deque<Contact> contacts, Settings settings, QuadConsumer<Advice, Status, ExposureOverTime, ExposureProximity> callback) {

    }
}
