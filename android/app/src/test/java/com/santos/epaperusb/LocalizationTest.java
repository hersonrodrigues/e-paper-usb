package com.santos.epaperusb;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.widget.Button;
import androidx.lifecycle.ViewModelProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class LocalizationTest {
    private Context locales(String tags) {
        Context app=RuntimeEnvironment.getApplication();
        Configuration config=new Configuration(app.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(tags));
        return app.createConfigurationContext(config);
    }
    @Test public void allRequestedLanguagesResolveAndFormatProgress() {
        String[][] cases={{"en-US","Select photo"},{"pt-BR","Selecionar foto"},{"pt-PT","Escolher fotografia"},
            {"es-MX","Seleccionar foto"},{"fr-FR","Choisir une photo"},{"it-IT","Scegli foto"},
            {"de-DE","Foto auswählen"},{"ko-KR","사진 선택"},{"ja-JP","写真を選択"},{"hi-IN","फ़ोटो चुनें"},
            {"zh-CN","选择照片"},{"zh-TW","選擇相片"},{"zh-HK","選擇相片"}};
        for(String[] entry:cases) {
            Context context=locales(entry[0]);
            assertEquals(entry[0],entry[1],context.getString(R.string.choose_photo));
            assertFalse(context.getString(R.string.help_body).isBlank());
            assertTrue(UiText.of(R.string.usb_progress,12).resolve(context).contains("12"));
            assertFalse(UiText.of(R.string.image_ready,96000).resolve(context).contains("%"));
        }
    }
    @Test public void languagePriorityAndEnglishFallbackFollowSystemPreferences() {
        assertEquals("Select photo",locales("ru-RU").getString(R.string.choose_photo));
        assertEquals("Select photo",locales("ru-RU,en-US,fr-FR").getString(R.string.choose_photo));
        assertEquals("Choisir une photo",locales("ru-RU,fr-FR,en-US").getString(R.string.choose_photo));
        assertEquals("Escolher fotografia",locales("pt-AO").getString(R.string.choose_photo));
        assertEquals("选择照片",locales("zh-SG").getString(R.string.choose_photo));
    }
    @Test public void retainedStatusUsesNewLocaleWithoutChangingImageBytes() {
        UiText status=UiText.of(R.string.usb_denied);
        assertEquals(locales("de-DE").getString(R.string.usb_denied),status.resolve(locales("de-DE")));
        assertEquals(locales("ja-JP").getString(R.string.usb_denied),status.resolve(locales("ja-JP")));
        assertNotEquals(status.resolve(locales("pt-BR")),status.resolve(locales("pt-PT")));
        RuntimeEnvironment.setQualifiers("en-rUS");
        var activity=Robolectric.buildActivity(MainActivity.class).setup();
        try {
            AppModel model=new ViewModelProvider(activity.get()).get(AppModel.class);
            var prepared=ProtocolTest.chart(); byte[] payload=prepared.payload();
            var bitmap=android.graphics.Bitmap.createBitmap(prepared.pixels(),800,480,android.graphics.Bitmap.Config.ARGB_8888);
            model.image.setValue(new AppModel.ImageState(prepared,bitmap,false,UiText.of(R.string.image_ready,96000)));
            RuntimeEnvironment.setQualifiers("pt-rPT"); activity.recreate();
            assertEquals("Escolher fotografia",((Button)activity.get().getWindow().getDecorView().findViewWithTag("choose")).getText().toString());
            AppModel retained=new ViewModelProvider(activity.get()).get(AppModel.class);
            assertSame(model,retained); assertSame(bitmap,retained.image.getValue().preview());
            assertArrayEquals(payload,retained.image.getValue().prepared().payload());
        } finally { activity.pause().stop().destroy(); }
    }
}
