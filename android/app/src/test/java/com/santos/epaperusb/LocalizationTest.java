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
            {"zh-CN","选择照片"},{"zh-TW","選擇相片"},{"zh-HK","選擇相片"},
            {"ar-EG","اختر صورة"},{"bn-BD","ছবি বাছুন"},{"ru-RU","Выбрать фото"},
            {"id-ID","Pilih foto"},{"in-ID","Pilih foto"},{"tr-TR","Fotoğraf seç"},
            {"vi-VN","Chọn ảnh"},{"th-TH","เลือกภาพ"},{"ur-PK","تصویر منتخب کریں"},
            {"fa-IR","انتخاب عکس"},{"pl-PL","Wybierz zdjęcie"},{"nl-NL","Foto kiezen"},
            {"uk-UA","Вибрати фото"},{"ta-IN","படத்தைத் தேர்ந்தெடு"}};
        for(String[] entry:cases) {
            Context context=locales(entry[0]);
            assertEquals(entry[0],entry[1],context.getString(R.string.choose_photo));
            assertFalse(context.getString(R.string.help_body).isBlank());
            String number=String.format(context.getResources().getConfiguration().getLocales().get(0),"%d",12);
            assertTrue(entry[0],UiText.of(R.string.usb_progress,12).resolve(context).contains(number));
            assertFalse(UiText.of(R.string.image_ready,96000).resolve(context).contains("%"));
        }
    }
    @Test public void languagePriorityAndEnglishFallbackFollowSystemPreferences() {
        assertEquals("Select photo",locales("fi-FI").getString(R.string.choose_photo));
        assertEquals("Select photo",locales("fi-FI,en-US,fr-FR").getString(R.string.choose_photo));
        assertEquals("Choisir une photo",locales("fi-FI,fr-FR,en-US").getString(R.string.choose_photo));
        assertEquals("Выбрать фото",locales("ru-RU,fr-FR,en-US").getString(R.string.choose_photo));
        assertEquals("اختر صورة",locales("fi-FI,ar-EG,en-US").getString(R.string.choose_photo));
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
            RuntimeEnvironment.setQualifiers("ar-rEG"); activity.recreate();
            assertEquals("اختر صورة",((Button)activity.get().getWindow().getDecorView().findViewWithTag("choose")).getText().toString());
            retained=new ViewModelProvider(activity.get()).get(AppModel.class);
            assertSame(model,retained); assertSame(bitmap,retained.image.getValue().preview());
            assertArrayEquals(payload,retained.image.getValue().prepared().payload());
        } finally { activity.pause().stop().destroy(); }
    }
    @Test public void rtlControlsMirrorButImagePreviewDoesNot() {
        for(String qualifier:new String[]{"ar-rEG","ur-rPK","fa-rIR"}) {
            RuntimeEnvironment.setQualifiers(qualifier);
            var activity=Robolectric.buildActivity(MainActivity.class).setup();
            try {
                android.view.View root=activity.get().getWindow().getDecorView();
                root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(2400,android.view.View.MeasureSpec.EXACTLY));
                root.layout(0,0,1080,2400);
                android.view.View choose=root.findViewWithTag("choose"), chart=root.findViewWithTag("chart");
                assertEquals(qualifier,android.view.View.LAYOUT_DIRECTION_RTL,choose.getLayoutDirection());
                assertTrue(qualifier,choose.getLeft()>chart.getLeft());
                assertEquals(android.view.View.LAYOUT_DIRECTION_LTR,root.findViewWithTag("preview").getLayoutDirection());
                assertFalse(root.findViewWithTag("send").isEnabled());
            } finally { activity.pause().stop().destroy(); }
        }
    }
}
