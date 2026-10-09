package dev.hyperos.notificationcount.render;

import static org.junit.Assert.*;

import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import dev.hyperos.notificationcount.core.AppIconSource;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppIconColorLoaderAndroidTest {
    @Test public void anAppWithNoDeclaredIconUsesFallbackAfterUserContextResolution() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        assertNull(AppIconColorLoader.load(context, new AppIconSource(context.getPackageName(), 0)));
    }

    @Test public void softwareDrawingSamplesColorAndRejectsMonochrome() {
        assertEquals(Integer.valueOf(0xff03d769), AppIconColorLoader.sample(new ColorDrawable(0xff03d769)));
        assertNull(AppIconColorLoader.sample(new ColorDrawable(Color.WHITE)));
        assertNull(AppIconColorLoader.sample(new ColorDrawable(Color.TRANSPARENT)));
    }

    @Test public void adaptiveIconBackgroundIsIncludedInTheCandidate() {
        AdaptiveIconDrawable icon = new AdaptiveIconDrawable(
                new ColorDrawable(0xff429cf5), new ColorDrawable(Color.TRANSPARENT));
        assertEquals(Integer.valueOf(0xff429cf5), AppIconColorLoader.sample(icon));
    }
}
