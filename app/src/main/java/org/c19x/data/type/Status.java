package org.c19x.data.type;

public enum Status {
    healthy, symptomatic, confirmedDiagnosis, infectious;

    public static Status forValue(int value) {
        switch (value) {
            case 0:
                return healthy;
            case 1:
                return symptomatic;
            case 2:
                return confirmedDiagnosis;
            case 3:
                return infectious;
            default:
                return null;
        }
    }

    public static int toValue(Status status) {
        switch (status) {
            case healthy:
                return 0;
            case symptomatic:
                return 1;
            case confirmedDiagnosis:
                return 2;
            case infectious:
                return 3;
            default:
                return 0;
        }
    }
}
