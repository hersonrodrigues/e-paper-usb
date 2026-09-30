package com.santos.epaperusb;

import android.graphics.*;
import com.santos.epaperusb.image.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageProcessorTest {
    @Test public void allEightExifOrientationsIncludingMirrors() {
        int a=Color.RED,b=Color.GREEN,c=Color.BLUE,d=Color.YELLOW,e=Color.CYAN,f=Color.MAGENTA;
        int[][] expected = {{a,b,c,d,e,f},{c,b,a,f,e,d},{f,e,d,c,b,a},{d,e,f,a,b,c},{a,d,b,e,c,f},{d,a,e,b,f,c},{f,c,e,b,d,a},{c,f,b,e,a,d}};
        Bitmap source = Bitmap.createBitmap(new int[]{a,b,c,d,e,f}, 3, 2, Bitmap.Config.ARGB_8888);
        for (int orientation=1; orientation<=8; orientation++) {
            Bitmap out = ImageProcessor.orient(source, orientation);
            assertEquals(orientation<=4?3:2, out.getWidth()); assertEquals(orientation<=4?2:3, out.getHeight());
            int[] pixels = new int[6]; out.getPixels(pixels, 0, out.getWidth(), 0, 0, out.getWidth(), out.getHeight());
            assertArrayEquals("EXIF="+orientation, expected[orientation-1], pixels);
            if (out != source) out.recycle();
        }
        source.recycle();
    }
    @Test public void aspectFitHasWhiteBordersAndCropFillsPanel() {
        Bitmap source = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888); source.eraseColor(Color.RED);
        Bitmap fit = ImageProcessor.fit(source, false), crop = ImageProcessor.fit(source, true);
        assertEquals(Color.WHITE, fit.getPixel(20, 240)); assertEquals(Color.RED, fit.getPixel(400, 240));
        assertEquals(Color.RED, crop.getPixel(20, 240)); assertEquals(Color.RED, crop.getPixel(799, 479));
        assertEquals(800, fit.getWidth()); assertEquals(480, fit.getHeight());
        source.recycle(); fit.recycle(); crop.recycle();
    }
    @Test public void canvasComposesTransparencyOntoWhite() {
        Bitmap transparent = Bitmap.createBitmap(80,48,Bitmap.Config.ARGB_8888);
        transparent.eraseColor(Color.TRANSPARENT);
        Bitmap out = ImageProcessor.fit(transparent, false);
        assertEquals(Color.WHITE, out.getPixel(400,240)); assertFalse(out.getPixel(400,240)==Color.TRANSPARENT);
        out.recycle(); transparent.recycle();
    }
    @Test public void decodeSamplingBoundsHugeAndPanoramicImages() throws Exception {
        for (int[] size : new int[][]{{100000,100000},{1000000,1},{1,1000000},{12000,8000},{800,480}}) {
            int sample=ImageProcessor.sampleSize(size[0],size[1]);
            long w=(long)Math.ceil(size[0]/(double)sample),h=(long)Math.ceil(size[1]/(double)sample);
            assertTrue(w*h<=3000000); assertTrue(Math.max(w,h)<=4096);
        }
        assertEquals(1, ImageProcessor.sampleSize(800,480));
        assertThrows(java.io.IOException.class,()->ImageProcessor.sampleSize(-1,400));
    }
    @Test public void diagnosticChartContainsEveryWireColor() {
        int[] pixels=ImageProcessor.testPattern().pixels();
        assertEquals(Color.BLACK,pixels[100*800+100]); assertEquals(Color.WHITE,pixels[100*800+300]);
        assertEquals(Color.YELLOW,pixels[100*800+500]); assertEquals(Color.RED,pixels[100*800+700]);
    }
}
