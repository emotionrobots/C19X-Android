package org.c19x.logic;

import org.c19x.data.type.Time;
import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.assertTrue;

public class ControllerTest {
    @Test
    public void testExample() throws ParseException {
        final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        final Date date = dateFormatter.parse("2020-06-18T00:00:00");
        final Date now = dateFormatter.parse("2020-06-19T05:59:00");
        assertTrue(ConcreteController.oncePerDay(new Time(date), new Time(now)));
    }
}
