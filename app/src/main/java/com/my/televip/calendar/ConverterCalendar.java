package com.my.televip.calendar;

import com.my.televip.Configs.ConfigManager;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.HijrahDate;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import ir.huri.jcal.JalaliCalendar;

public class ConverterCalendar {

    public static HijrahDate toHijri(long date) {
        ZonedDateTime zdt = Instant
                .ofEpochMilli(date)
                .atZone(ZoneId.systemDefault());

        return HijrahDate.from(zdt);
    }


    public static HijrahDate toHijri(Calendar calendar) {
        ZonedDateTime zdt = Instant
                .ofEpochMilli(calendar.getTimeInMillis())
                .atZone(ZoneId.systemDefault());

        return HijrahDate.from(zdt);
    }

    public static HijrahDate toHijri(Date date) {
        ZonedDateTime zdt = Instant
                .ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault());

        return HijrahDate.from(zdt);
    }

    public static JalaliCalendar toJalali(long date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);

        return new JalaliCalendar(calendar.getTime());
    }


    public static JalaliCalendar toJalali(Calendar calendar) {
        return new JalaliCalendar(calendar.getTime());
    }

    public static JalaliCalendar toJalali(Date date) {
        return new JalaliCalendar(date);
    }

    public static CalendarDate toCalendar(long date) {
        if (ConfigManager.customCalendar == null){
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(date);
            return new CalendarDate(calendar);
        }
        if (ConfigManager.customCalendar.getCustomCalendar() == 1) {
            return new CalendarDate(toHijri(date));
        } else if (ConfigManager.customCalendar.getCustomCalendar() == 2) {
            return new CalendarDate(toJalali(date));
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);
        return new CalendarDate(calendar);
    }

    public static CalendarDate toCalendar(Date date) {
        if (ConfigManager.customCalendar.getCustomCalendar() == 1) {
            return new CalendarDate(toHijri(date));
        } else if (ConfigManager.customCalendar.getCustomCalendar() == 2) {
            return new CalendarDate(toJalali(date));
        }
        return null;
    }

    public static CalendarDate toCalendar(Calendar calendar) {
        if (ConfigManager.customCalendar == null){
            return new CalendarDate(calendar);
        }
        if (ConfigManager.customCalendar.getCustomCalendar() == 1) {
            return new CalendarDate(toHijri(calendar));
        } else if (ConfigManager.customCalendar.getCustomCalendar() == 2) {
            return new CalendarDate(toJalali(calendar));
        }
        return new CalendarDate(calendar);
    }

    public static String formatDate(long date) {
        try {
            Calendar now = Calendar.getInstance();
            now.setTimeInMillis(System.currentTimeMillis());
            CalendarDate rightNow = ConverterCalendar.toCalendar(now);

            int day = rightNow.getDay();
            int year = rightNow.getYear();


            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            String dateHour = sdf.format(new Date(date));

            rightNow = ConverterCalendar.toCalendar(date);

            int dateDay = rightNow.getDay();
            int dateYear = rightNow.getYear();
            int dateMonth = rightNow.getMonth();
            String dateMonthName = rightNow.getMonthName();


            Calendar yesterday = (Calendar) now.clone();
            yesterday.add(Calendar.DAY_OF_MONTH, -1);
            CalendarDate previousDay = ConverterCalendar.toCalendar(yesterday);
            if (dateDay == day && year == dateYear && dateMonth == ConverterCalendar.toCalendar(now).getMonth()) {
                return Translator.get(Keys.TodayAt) + dateHour;
            } else if (dateDay == previousDay.getDay() && dateYear == previousDay.getYear()
                    && dateMonth == previousDay.getMonth()) {
                return Translator.get(Keys.YesterdayAt) + dateHour;
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return Translator.get(Keys.Edited) + dateDay + " " + dateMonthName + " " + dateHour;
            } else {
                return Translator.get(Keys.Edited) + dateYear + "/" + dateMonth + "/" + dateDay + " " + dateHour;
            }
        } catch (Exception e) {}
        return null;
    }

}
