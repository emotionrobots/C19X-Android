package org.c19x.data.type;

public enum Advice {
    normal, stayAtHome, selfIsolation;

    public static Advice forValue(int value) {
        switch (value) {
            case 0:
                return normal;
            case 1:
                return stayAtHome;
            case 2:
                return selfIsolation;
            default:
                return null;
        }
    }

    public static int toValue(Advice advice) {
        switch (advice) {
            case normal:
                return 0;
            case stayAtHome:
                return 1;
            case selfIsolation:
                return 2;
            default:
                return 0;
        }
    }

}
