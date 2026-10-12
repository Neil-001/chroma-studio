package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Oklch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TargetModelTest {
    @Test
    void everyFieldShowsTheSameColour() {
        TargetModel m = new TargetModel(Color.ofRgb(0xFF8800));
        assertEquals("#FF8800", m.hex());
        assertEquals("255,136,0", m.rgbText());
        assertEquals("32,100,50", m.hslText());
        assertEquals(Color.ofRgb(0xFF8800).oklch(), m.lch());
        assertEquals(32, m.hsv()[0], 0.01);
    }

    @Test
    void typingInOneFieldMovesTheRest() {
        TargetModel m = new TargetModel(Color.ofRgb(0x000000));
        assertTrue(m.typeHex("3a7d44"));
        assertEquals("58,125,68", m.rgbText());
        assertTrue(m.typeRgb("255, 136, 0"));
        assertEquals("#FF8800", m.hex());
        assertTrue(m.typeHsl("210 50% 40%"));
        assertEquals(Color.fromHsl(210, 0.5f, 0.4f), m.colour());
        assertEquals(m.colour().oklch(), m.lch(), "the sliders follow a typed colour");
    }

    @Test
    void textThatIsNotAColourChangesNothing() {
        TargetModel m = new TargetModel(Color.ofRgb(0x123456));
        for (String bad : new String[] {"", "#12345", "12345g", "#1234567"}) assertFalse(m.typeHex(bad), bad);
        for (String bad : new String[] {"", "1,2", "1,2,3,4", "256,0,0", "-1,0,0", "a,b,c"}) assertFalse(m.typeRgb(bad), bad);
        for (String bad : new String[] {"361,50,50", "10,101,50", "10,50", "x"}) assertFalse(m.typeHsl(bad), bad);
        assertEquals("#123456", m.hex());
    }

    @Test
    void aSliderSetsTheColourAndKeepsItsOwnPlace() {
        TargetModel m = new TargetModel(Color.ofRgb(0x808080));
        Oklch inside = new Oklch(0.7, 0.1, 150);
        m.setLch(inside);
        assertEquals(inside, m.lch());
        assertEquals(Color.ofOklch(inside), m.colour());
        assertEquals(m.colour().hex(), m.hex(), "the hex field follows the slider");

        // More chroma than sRGB has at this lightness and hue: the colour is clipped, the handle stays put.
        Oklch outside = new Oklch(0.7, 0.39, 150);
        assertFalse(outside.inGamut());
        m.setLch(outside);
        assertEquals(outside, m.lch());
        assertEquals(Color.ofOklch(outside), m.colour());
        assertTrue(m.colour().oklch().c() < 0.39);

        m.setLch(new Oklch(2, 9, 0));
        assertEquals(1, m.lch().l());
        assertEquals(TargetModel.CHROMA_MAX, m.lch().c());
    }

    @Test
    void theHueSliderReachesItsEndWithoutJumpingToTheStart() {
        TargetModel m = new TargetModel(Color.ofRgb(0xCC4444));
        m.setLch(m.lch().withHue(TargetModel.HUE_MAX));
        assertEquals(TargetModel.HUE_MAX, m.lch().h(), 1e-9);
    }

    @Test
    void aGreyKeepsTheHueThePickersShowed() {
        TargetModel m = new TargetModel(Color.ofRgb(0x2060C0));
        float hue = m.hsv()[0];
        double lchHue = m.lch().h();
        m.setHsv(hue, 0, 0.5f);
        assertEquals(hue, m.hsv()[0], "the square's own hue");
        assertEquals(lchHue, m.lch().h(), 1e-9, "the hue slider doesn't swing on a grey");
        m.setColour(Color.ofRgb(0x000000));
        assertEquals(hue, m.hsv()[0]);
        m.setColour(Color.ofRgb(0x00FF00));
        assertEquals(120, m.hsv()[0], 0.01, "a colour with a hue brings its own");
    }

    @Test
    void theSquareSetsTheColour() {
        TargetModel m = new TargetModel(Color.ofRgb(0x000000));
        m.setHsv(0, 1, 1);
        assertEquals("#FF0000", m.hex());
        m.setHsv(500, -1, 2);
        assertEquals("#FFFFFF", m.hex(), "figures are held within their ranges");
    }
}
