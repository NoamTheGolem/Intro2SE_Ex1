package ex1.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Grey mapping (decision D7).
 */
public class IntensityWindowTest
{
    @Test
    public void mapsLinearlyAndClamps()
    {
        IntensityWindow w = new IntensityWindow(100, 200);
        assertEquals(0, w.grey(50));
        assertEquals(0, w.grey(100));
        // 160 -> (160-100)*2.55 = 153 exactly (150 would be a 127.5 rounding tie).
        assertEquals(153, w.grey(160));
        assertEquals(255, w.grey(200));
        assertEquals(255, w.grey(1e9));
    }

    @Test
    public void percentilesIgnoreOutliers()
    {
        Volume v = Volume.create(VoxelType.UINT16, 100, 100, 10);
        for (int i = 0; i < v.voxelCount(); i++)
        {
            v.setValue(i, i % 1000);
        }
        v.setValue(0, 65000);
        IntensityWindow w = IntensityWindow.fromPercentiles(v, 1, 99);
        assertTrue("low near 10, was " + w.low(), w.low() >= 0 && w.low() < 30);
        assertTrue("high near 990, was " + w.high(), w.high() > 900 && w.high() < 1100);
    }

    @Test
    public void constantVolumeDoesNotDivideByZero()
    {
        Volume v = Volume.create(VoxelType.UINT8, 4, 4, 4);
        IntensityWindow w = IntensityWindow.fromPercentiles(v, 0.5, 99.5);
        assertTrue(w.high() > w.low());
    }

    @Test
    public void toGreyCentresImageInFrame()
    {
        Image2D img = new Image2D(2, 2);
        img.pixels()[0] = 255;
        img.pixels()[3] = 255;
        byte[] frame = new byte[4 * 4];
        new IntensityWindow(0, 255).toGrey(img, frame, 4, 4, 2, 2);
        assertEquals(255, frame[1 * 4 + 1] & 0xFF);
        assertEquals(0, frame[1 * 4 + 2] & 0xFF);
        assertEquals(255, frame[2 * 4 + 2] & 0xFF);
        assertEquals(0, frame[0] & 0xFF);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyWindow()
    {
        new IntensityWindow(5, 5);
    }
}
