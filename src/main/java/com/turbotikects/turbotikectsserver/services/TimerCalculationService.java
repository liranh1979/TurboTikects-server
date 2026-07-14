package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.HoursOfOperationEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates a deadline by walking forward through a company's Hours of Operation schedule,
 * accumulating only business hours until the requested duration is consumed.
 *
 * Unit "hours" → durationValue working hours.
 * Unit "days"  → durationValue × 8 working hours (standard business-day assumption).
 */
@Service
public class TimerCalculationService {

    public LocalDateTime calculateTarget(LocalDateTime startedAt,
                                         double durationValue,
                                         String durationUnit,
                                         List<HoursOfOperationEntity> schedule) {
        double totalHours = "days".equalsIgnoreCase(durationUnit)    ? durationValue * 8.0
                          : "minutes".equalsIgnoreCase(durationUnit) ? durationValue / 60.0
                          :                                             durationValue;  // hours

        if (schedule == null || schedule.isEmpty()) {
            // No HoO configured: treat all hours as business hours
            return startedAt.plusMinutes(Math.round(totalHours * 60));
        }

        LocalDateTime current = startedAt;
        double remaining = totalHours;
        int safetyLimit = 365; // never loop more than a year

        while (remaining > 0 && safetyLimit-- > 0) {
            int dayOfWeek = current.getDayOfWeek().getValue() % 7; // Mon=1..Sun=7 → 0=Sun,1=Mon..6=Sat
            // Java DayOfWeek: MON=1..SUN=7; convert to 0=Sun..6=Sat used by the entity
            dayOfWeek = current.getDayOfWeek().getValue() % 7; // SUN=7%7=0, MON=1, ..., SAT=6

            HoursOfOperationEntity day = null;
            for (HoursOfOperationEntity h : schedule) {
                if (h.getDayOfWeek() == dayOfWeek) { day = h; break; }
            }

            if (day == null || !day.isOpen()) {
                // Skip to start of next calendar day
                current = LocalDate.from(current).plusDays(1).atStartOfDay();
                continue;
            }

            List<long[]> slots = parseSlotsForDay(day);
            if (slots.isEmpty()) {
                current = LocalDate.from(current).plusDays(1).atStartOfDay();
                continue;
            }

            boolean advancedToday = false;
            for (long[] slot : slots) {
                LocalDateTime slotStart = LocalDate.from(current).atTime(LocalTime.ofSecondOfDay(slot[0]));
                LocalDateTime slotEnd   = LocalDate.from(current).atTime(LocalTime.ofSecondOfDay(slot[1]));

                if (current.isAfter(slotEnd) || current.isEqual(slotEnd)) continue; // past slot

                if (current.isBefore(slotStart)) current = slotStart; // jump to slot open

                double availableHours = java.time.Duration.between(current, slotEnd).toSeconds() / 3600.0;
                if (availableHours >= remaining) {
                    return current.plusMinutes(Math.round(remaining * 60));
                }
                remaining -= availableHours;
                current = slotEnd;
                advancedToday = true;
            }

            // Move to next day
            current = LocalDate.from(current).plusDays(1).atStartOfDay();
        }

        // Fallback: shouldn't happen; return a rough estimate
        return startedAt.plusHours((long) totalHours);
    }

    /**
     * Walks the same business-hours slots as {@link #calculateTarget}, but accumulates elapsed
     * business minutes from {@code startedAt} up to {@code now} instead of stopping at a target
     * duration. Needed because comparing raw wall-clock timestamps (e.g. {@code now - startedAt})
     * against a business-hours-computed target is wrong whenever the elapsed window spans
     * non-business time (nights/weekends) — it would report a percentage-elapsed that doesn't
     * reflect any actual business time having passed. Used for the SLA countdown's elapsed % and
     * the escalation ladder's percent-of-SLA-used triggers, so both agree with each other and with
     * the business-hours-aware target this same class computes.
     */
    public double calculateElapsedBusinessMinutes(LocalDateTime startedAt,
                                                    LocalDateTime now,
                                                    List<HoursOfOperationEntity> schedule) {
        if (now.isBefore(startedAt)) return 0;

        if (schedule == null || schedule.isEmpty()) {
            // No HoO configured: treat all hours as business hours (24×7 policy)
            return java.time.Duration.between(startedAt, now).toSeconds() / 60.0;
        }

        LocalDateTime current = startedAt;
        double elapsedMinutes = 0;
        int safetyLimit = 365; // never loop more than a year

        while (current.isBefore(now) && safetyLimit-- > 0) {
            int dayOfWeek = current.getDayOfWeek().getValue() % 7; // SUN=7%7=0, MON=1, ..., SAT=6

            HoursOfOperationEntity day = null;
            for (HoursOfOperationEntity h : schedule) {
                if (h.getDayOfWeek() == dayOfWeek) { day = h; break; }
            }

            if (day == null || !day.isOpen()) {
                current = LocalDate.from(current).plusDays(1).atStartOfDay();
                continue;
            }

            List<long[]> slots = parseSlotsForDay(day);
            if (slots.isEmpty()) {
                current = LocalDate.from(current).plusDays(1).atStartOfDay();
                continue;
            }

            for (long[] slot : slots) {
                LocalDateTime slotStart = LocalDate.from(current).atTime(LocalTime.ofSecondOfDay(slot[0]));
                LocalDateTime slotEnd   = LocalDate.from(current).atTime(LocalTime.ofSecondOfDay(slot[1]));

                if (current.isAfter(slotEnd) || current.isEqual(slotEnd)) continue; // already past this slot
                if (current.isBefore(slotStart)) current = slotStart; // jump to slot open (gap before open is unpaid)

                if (now.isBefore(slotEnd)) {
                    // "now" falls inside this slot — count up to now and stop
                    if (now.isAfter(current)) {
                        elapsedMinutes += java.time.Duration.between(current, now).toSeconds() / 60.0;
                    }
                    return elapsedMinutes;
                }

                // whole slot has already elapsed
                elapsedMinutes += java.time.Duration.between(current, slotEnd).toSeconds() / 60.0;
                current = slotEnd;
            }

            current = LocalDate.from(current).plusDays(1).atStartOfDay();
        }

        return elapsedMinutes;
    }

    /** Converts an HoO entity's slot strings to seconds-of-day pairs. */
    private List<long[]> parseSlotsForDay(HoursOfOperationEntity day) {
        List<long[]> slots = new ArrayList<>();
        if (day.getSlot1Start() != null && day.getSlot1End() != null) {
            slots.add(new long[]{
                LocalTime.parse(day.getSlot1Start()).toSecondOfDay(),
                LocalTime.parse(day.getSlot1End()).toSecondOfDay()
            });
        }
        if (day.getSlot2Start() != null && day.getSlot2End() != null) {
            slots.add(new long[]{
                LocalTime.parse(day.getSlot2Start()).toSecondOfDay(),
                LocalTime.parse(day.getSlot2End()).toSecondOfDay()
            });
        }
        return slots;
    }
}
