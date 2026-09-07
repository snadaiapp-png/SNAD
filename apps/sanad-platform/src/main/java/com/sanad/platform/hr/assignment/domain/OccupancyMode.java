package com.sanad.platform.hr.assignment.domain;

/**
 * Occupancy mode — whether this assignment occupies a Position seat.
 *
 * <p>OCCUPYING means the assignment consumes the Position's seat
 * exclusivity (only one OCCUPYING assignment per Position per period).
 * NON_OCCUPYING means the assignment references the Position but does
 * not consume seat exclusivity.</p>
 */
public enum OccupancyMode {
    OCCUPYING,
    NON_OCCUPYING
}
