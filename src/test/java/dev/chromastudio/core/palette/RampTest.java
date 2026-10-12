package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.color.Oklch;
import java.util.List;
import org.junit.jupiter.api.Test;

class RampTest {
    private static final Oklch RED = new Oklch(0.6, 0.2, 30), BLUE = new Oklch(0.4, 0.1, 250), GREEN = new Oklch(0.8, 0.15, 140);

    private static void assertColour(double l, double c, double h, Oklch actual) {
        assertEquals(l, actual.l(), 1e-9, "lightness of " + actual);
        assertEquals(c, actual.c(), 1e-9, "chroma of " + actual);
        assertEquals(h, actual.h(), 1e-9, "hue of " + actual);
    }

    @Test
    void easingValues() {
        for (Easing e : Easing.values()) {
            assertEquals(0, e.apply(0), e.toString());
            assertEquals(1, e.apply(1), e.toString());
        }
        assertEquals(0.5, Easing.LINEAR.apply(0.5));
        assertEquals(0.5, Easing.SMOOTH.apply(0.5));
        assertEquals(0.25, Easing.EASE_IN.apply(0.5));
        assertEquals(0.75, Easing.EASE_OUT.apply(0.5));
        assertEquals(0.15625, Easing.SMOOTH.apply(0.25));
    }

    @Test
    void hueArcTurns() {
        assertEquals(40, HueArc.SHORTEST.turn(350, 30), 1e-9);
        assertEquals(-40, HueArc.SHORTEST.turn(30, 350), 1e-9);
        assertEquals(-320, HueArc.LONGEST.turn(350, 30), 1e-9);
        assertEquals(320, HueArc.LONGEST.turn(30, 350), 1e-9);
        assertEquals(0, HueArc.SHORTEST.turn(90, 90), 1e-9);
    }

    @Test
    void twoStopsAndTwoStepsAreTheStops() {
        for (Easing e : Easing.values()) {
            for (HueArc arc : HueArc.values()) {
                assertEquals(List.of(RED, BLUE), Ramp.of(List.of(RED, BLUE), 2, e, arc));
            }
        }
    }

    @Test
    void theEndsAreAlwaysTheFirstAndLastStops() {
        for (Easing e : Easing.values()) {
            for (int steps : new int[] {2, 3, 7, 32}) {
                List<Oklch> ramp = Ramp.of(List.of(RED, GREEN, BLUE), steps, e, HueArc.SHORTEST);
                assertEquals(steps, ramp.size());
                assertEquals(RED, ramp.getFirst());
                assertEquals(BLUE, ramp.getLast());
            }
        }
    }

    @Test
    void aLinearRampGoesInEvenSteps() {
        List<Oklch> ramp = Ramp.of(List.of(new Oklch(0.2, 0.1, 100), new Oklch(0.6, 0.2, 140)), 5, Easing.LINEAR, HueArc.SHORTEST);
        assertColour(0.2, 0.1, 100, ramp.get(0));
        assertColour(0.3, 0.125, 110, ramp.get(1));
        assertColour(0.4, 0.15, 120, ramp.get(2));
        assertColour(0.5, 0.175, 130, ramp.get(3));
        assertColour(0.6, 0.2, 140, ramp.get(4));
    }

    @Test
    void stopsSitAtEqualIntervals() {
        // Seven steps over three stops: the middle stop is step 3, with two steps inside each stretch.
        List<Oklch> ramp = Ramp.of(List.of(RED, GREEN, BLUE), 7, Easing.LINEAR, HueArc.SHORTEST);
        assertColour(GREEN.l(), GREEN.c(), GREEN.h(), ramp.get(3));
        assertColour(0.6 + 0.2 / 3, 0.2 - 0.05 / 3, 30 + 110.0 / 3, ramp.get(1));
        assertColour(0.8 - 0.4 * 2 / 3, 0.15 - 0.05 * 2 / 3, 140 + 110.0 * 2 / 3, ramp.get(5));

        // Four stops and ten steps: each stretch holds a third of the parameter.
        List<Oklch> four = Ramp.of(List.of(RED, GREEN, BLUE, RED), 10, Easing.LINEAR, HueArc.SHORTEST);
        assertColour(GREEN.l(), GREEN.c(), GREEN.h(), four.get(3));
        assertColour(BLUE.l(), BLUE.c(), BLUE.h(), four.get(6));
    }

    @Test
    void easingPacesTheWholeRampNotEachStretch() {
        // Ease-in at the halfway step is a quarter of the way along, which is halfway through the first stretch.
        List<Oklch> ramp = Ramp.of(List.of(RED, GREEN, BLUE), 3, Easing.EASE_IN, HueArc.SHORTEST);
        assertColour(0.7, 0.175, 85, ramp.get(1));
        // Ease-out is three quarters along: halfway through the second.
        ramp = Ramp.of(List.of(RED, GREEN, BLUE), 3, Easing.EASE_OUT, HueArc.SHORTEST);
        assertColour(0.6, 0.125, 195, ramp.get(1));
    }

    @Test
    void theShortestArcCrossesTheSeam() {
        List<Oklch> ramp = Ramp.of(List.of(new Oklch(0.5, 0.1, 350), new Oklch(0.5, 0.1, 30)), 5, Easing.LINEAR, HueArc.SHORTEST);
        double[] hues = {350, 0, 10, 20, 30};
        for (int i = 0; i < 5; i++) assertEquals(hues[i], ramp.get(i).h(), 1e-9);

        ramp = Ramp.of(List.of(new Oklch(0.5, 0.1, 30), new Oklch(0.5, 0.1, 350)), 5, Easing.LINEAR, HueArc.SHORTEST);
        for (int i = 0; i < 5; i++) assertEquals(hues[4 - i], ramp.get(i).h(), 1e-9);
    }

    @Test
    void theLongestArcGoesTheOtherWayRound() {
        List<Oklch> ramp = Ramp.of(List.of(new Oklch(0.5, 0.1, 350), new Oklch(0.5, 0.1, 30)), 5, Easing.LINEAR, HueArc.LONGEST);
        double[] hues = {350, 270, 190, 110, 30};
        for (int i = 0; i < 5; i++) assertEquals(hues[i], ramp.get(i).h(), 1e-9);

        // Stops that the shortest arc joins without the seam cross it the long way.
        ramp = Ramp.of(List.of(new Oklch(0.5, 0.1, 60), new Oklch(0.5, 0.1, 100)), 5, Easing.LINEAR, HueArc.LONGEST);
        hues = new double[] {60, 340, 260, 180, 100};
        for (int i = 0; i < 5; i++) assertEquals(hues[i], ramp.get(i).h(), 1e-9);
    }

    @Test
    void aGreyStopTakesTheOtherEndsHue() {
        Oklch grey = new Oklch(0.9, 0.005, 200);
        for (HueArc arc : HueArc.values()) {
            List<Oklch> ramp = Ramp.of(List.of(grey, RED), 5, Easing.LINEAR, arc);
            assertEquals(grey, ramp.getFirst());
            for (int i = 1; i < 5; i++) assertEquals(30, ramp.get(i).h(), 1e-9, arc + " step " + i);
            assertColour(0.75, 0.1025, 30, ramp.get(2));

            ramp = Ramp.of(List.of(RED, grey), 5, Easing.LINEAR, arc);
            assertEquals(grey, ramp.getLast());
            for (int i = 0; i < 4; i++) assertEquals(30, ramp.get(i).h(), 1e-9, arc + " step " + i);

            // Between two greys nothing turns either.
            ramp = Ramp.of(List.of(grey, new Oklch(0.2, 0, 0)), 4, Easing.LINEAR, arc);
            for (int i = 0; i < 3; i++) assertEquals(200, ramp.get(i).h(), 1e-9, arc + " step " + i);
        }
        // A stop just over the limit is a colour, and turns.
        List<Oklch> ramp = Ramp.of(List.of(new Oklch(0.9, 0.01, 200), RED), 3, Easing.LINEAR, HueArc.SHORTEST);
        assertEquals(115, ramp.get(1).h(), 1e-9);
    }

    @Test
    void aGreyInTheMiddleKeepsEachSidesHue() {
        Oklch grey = new Oklch(0.5, 0, 0);
        List<Oklch> ramp = Ramp.of(List.of(RED, grey, BLUE), 5, Easing.LINEAR, HueArc.SHORTEST);
        assertEquals(30, ramp.get(1).h(), 1e-9);
        assertEquals(250, ramp.get(3).h(), 1e-9);
    }

    @Test
    void tooFewStopsOrStepsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Ramp.of(List.of(RED), 4, Easing.LINEAR, HueArc.SHORTEST));
        assertThrows(IllegalArgumentException.class, () -> Ramp.of(List.of(RED, BLUE), 1, Easing.LINEAR, HueArc.SHORTEST));
        assertTrue(Ramp.of(List.of(RED, BLUE), 2, Easing.LINEAR, HueArc.SHORTEST).size() == 2);
    }
}
