package com.gswxxn.restoresplashscreen.hook

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.data.RoundDegree
import com.gswxxn.restoresplashscreen.utils.CommonUtils.isAtLeastT
import com.gswxxn.restoresplashscreen.utils.DataCacheUtils.checkDarkModeChanged
import com.gswxxn.restoresplashscreen.utils.DataCacheUtils.colorData
import com.gswxxn.restoresplashscreen.utils.DataCacheUtils.iconData
import com.gswxxn.restoresplashscreen.utils.GraphicUtils
import com.gswxxn.restoresplashscreen.utils.IconPackManager
import com.gswxxn.restoresplashscreen.utils.YukiHelper.getField
import com.gswxxn.restoresplashscreen.utils.YukiHelper.getMapPrefs
import com.gswxxn.restoresplashscreen.utils.YukiHelper.printLog
import com.gswxxn.restoresplashscreen.utils.YukiHelper.register
import com.gswxxn.restoresplashscreen.utils.YukiHelper.setField
import com.highcapable.yukihookapi.hook.bean.VariousClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.type.android.ActivityInfoClass
import com.highcapable.yukihookapi.hook.type.android.DrawableClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringType
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SystemUIHooker: YukiBaseHooker() {
    private val iconPackManager by lazy { IconPackManager(
        appContext!!,
        prefs.get(DataConst.ICON_PACK_PACKAGE_NAME)
    ) }

    private fun isExcept(pkgName: String): Boolean {
        val list = prefs.get(DataConst.CUSTOM_SCOPE_LIST)
        val isExceptionMode = prefs.get(DataConst.IS_CUSTOM_SCOPE_EXCEPTION_MODE)
        return prefs.get(DataConst.ENABLE_CUSTOM_SCOPE)
                && ((isExceptionMode && (pkgName in list)) || (!isExceptionMode && pkgName !in list))
    }

    override fun onHook() {
        register()

        var currentPackageName: String? = null

        /**
         * ??????????????????
         *
         * ????????? makeSplashScreenContentView() ?????????
         *
         * ??????????????? makeSplashScreenContentView() ?????? if ??????
         * - ??????????????? ???????????????????????????????????????????????????????????????
         */
        findClass("com.android.wm.shell.startingsurface.SplashscreenContentDrawer").hook {

            injectMember {
                method {
                    name = "getBGColorFromCache"
                    paramCount(2)
                }
                beforeHook {
                    appContext
                    if (!prefs.get(DataConst.FORCE_ENABLE_SPLASH_SCREEN)) {
                        val pkgName = args(0).cast<ActivityInfo>()?.packageName!!
                        val isExcept = isExcept(pkgName)
                        if (!isExcept)
                            instance.getField("mTmpAttrs")!!.setField("mIconBgColor", 1)
                        printLog(
                            "****** ${pkgName}:",
                            "1. getBGColorFromCache(): ${
                                if (isExcept) "Except this app" else "Not Except, Set mIconBgColor 1"
                            }"
                        )
                    }
                }
            }
        }

        /**
         * ?????????????????????
         * - ????????????????????????
         * - ?????????????????????????????????
         * - ??????????????????
         * - ??????????????????
         * - ??????????????????????????????
         */
        findClass("com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$StartingWindowViewBuilder")
            .hook {

                /**
                 * ?????????????????????
                 * - ?????????????????????????????????
                 * - ??????????????????
                 */
                injectMember {
                    method {
                        name = "build"
                        emptyParam()
                    }
                    beforeHook {
                        val pkgName = instance.getField<ActivityInfo>("mActivityInfo")!!.packageName!!
                            .also { currentPackageName = it }
                        val isDefaultStyle = prefs.get(DataConst.ENABLE_DEFAULT_STYLE)
                                && pkgName in prefs.get(DataConst.DEFAULT_STYLE_LIST)
                        val isRemoveBrandingImage = prefs.get(DataConst.REMOVE_BRANDING_IMAGE)
                                && pkgName in prefs.get(DataConst.REMOVE_BRANDING_IMAGE_LIST)
                        val isRemoveBGColor = prefs.get(DataConst.REMOVE_BG_COLOR)
                        val isReplaceToEmptySplashScreen = prefs.get(DataConst.REPLACE_TO_EMPTY_SPLASH_SCREEN)
                        val isExcept = isExcept(pkgName)
                        val forceEnableSplashScreen = prefs.get(DataConst.FORCE_ENABLE_SPLASH_SCREEN)
                        val context = instance.getField<Context>("mContext")!!
                        val mSplashscreenContentDrawer = instance.getField("this\$0")!!
                        val mTmpAttrs = mSplashscreenContentDrawer
                            .getField("mTmpAttrs")!!

                        /**
                         * ????????????????????????
                         *
                         * ???????????? build() ?????? if ??????
                         */
                        if (forceEnableSplashScreen) {
                            if (!isExcept) {
                                instance.setField("mSuggestType", 1)
                                instance.setField("mOverlayDrawable", null)
                            }
                            printLog(
                                "****** ${pkgName}(forceEnableSplashScreen):",
                                "1. build(): ${
                                    if (isExcept) "Except this app"
                                    else "set mSuggestType to 1; set mOverlayDrawable to null"}"
                            )
                        }

                        // ????????????
                        printLog("info: build(): mSuggestType is ${instance.getField("mSuggestType")}")

                        // ???????????????????????????????????????????????? mTmpAttrs
                        mSplashscreenContentDrawer.current {
                            method {
                                name = "getWindowAttrs"
                                paramCount(2)
                            }.call(context, mTmpAttrs)
                        }
                        printLog("2. build(): call getWindowAttrs() to reset mTmpAttrs")

                        // ???????????????????????????????????????????????????
                        if (isReplaceToEmptySplashScreen && isExcept && mTmpAttrs.getField("mSplashScreenIcon") == null) {
                            instance.setField("mSuggestType", 3)
                            instance.setField("mOverlayDrawable", context.getDrawable(mTmpAttrs.getField<Int>("mWindowBgResId")!!))
                        }
                        printLog("2.1. build(): ${if (isReplaceToEmptySplashScreen && isExcept) "set mSuggestType to 3;" else "Not"} replace to empty splash screen")

                        /**
                         * ?????????????????????????????????
                         *
                         * ?????? build() ?????? if ??????
                         */
                        if (isDefaultStyle) {
                            mTmpAttrs.setField("mSplashScreenIcon", null)
                        }
                        printLog(
                            "3. build(): ${if (isDefaultStyle) "" else "Not"} ignore set icon"
                        )

                        /**
                         * ??????????????????
                         *
                         * ?????? fillViewWithIcon() ?????? if ??????
                         */
                        if (isRemoveBrandingImage) {
                            mTmpAttrs.setField("mBrandingImage", null)
                        }
                        printLog(
                            "4. build(): ${if (isRemoveBrandingImage) "" else "Not"} remove Branding Image"
                        )

                        /**
                         * ??????????????????
                         */
                        if (isRemoveBGColor) {
                            instance.setField("mThemeColor", Color.parseColor("#F5F5F5"))
                        }
                        printLog(
                            "5. build(): ${if (isRemoveBGColor) "" else "Not"} remove BG Color"
                        )

                        // ??????????????????
                        if (instance.getField("mSuggestType") == 1 &&
                            mTmpAttrs.getField("mSplashScreenIcon") == null) {
                            instance.current().method { name = "createIconDrawable" }.apply {
                                if (isAtLeastT)
                                    call(
                                        null,
                                        true,
                                        mSplashscreenContentDrawer.getField("mHighResIconProvider")!!
                                            .getField("mLoadInDetail")
                                    )
                                else call(null, true)
                            }
                            result = instance.current().method { name = "fillViewWithIcon" }.run {
                                if (isAtLeastT)
                                    call(
                                        instance.getField("mFinalIconSize"),
                                        instance.getField("mFinalIconDrawables"),
                                        instance.getField("mUiThreadInitTask")
                                    )
                                else
                                    call(
                                        instance.getField("mFinalIconSize"),
                                        instance.getField("mFinalIconDrawables"),
                                        mTmpAttrs.getField("mAnimationDuration")
                                    )
                            }
                        }
                    }
                }

                /**
                 * ?????????????????????
                 * - ??????????????????
                 * - ??????????????????????????????
                 *
                 * ??????????????? createIconDrawable() ???????????? Splash Screen ?????????????????????????????????????????????
                 * ???????????????????????? Palette API ????????????????????????????????????????????????????????????????????????????????? mThemeColor
                 * ????????????????????????????????????
                 */
                injectMember {
                    method {
                        name = "createIconDrawable"
                        if (isAtLeastT) param(DrawableClass, BooleanType, BooleanType)
                        else param(DrawableClass, BooleanType)
                    }
                    beforeHook {
                        val bgColorType = prefs.get(DataConst.CHANG_BG_COLOR_TYPE)
                        val ignoreDarkMode = prefs.get(DataConst.IGNORE_DARK_MODE)
                        val colorMode = prefs.get(DataConst.BG_COLOR_MODE)
                        val enableDataCache = prefs.get(DataConst.ENABLE_DATA_CACHE)
                        val isDarkMode = (appContext!!.resources
                            .configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                            .also { checkDarkModeChanged(it) }
                        val individualBgColorAppMap = getMapPrefs(
                            if (!isDarkMode) DataConst.INDIVIDUAL_BG_COLOR_APP_MAP
                            else DataConst.INDIVIDUAL_BG_COLOR_APP_MAP_DARK)
                        val pkgName =
                            instance.getField<ActivityInfo>("mActivityInfo")!!.packageName
                        val mSplashscreenContentDrawer = instance.getField("this\$0")!!
                        val isInExceptList =
                            pkgName in prefs.get(DataConst.BG_EXCEPT_LIST) || isExcept(pkgName)
                        val skipAppWithBgColor = bgColorType != 0 &&
                                pkgName !in individualBgColorAppMap.keys &&
                                prefs.get(DataConst.SKIP_APP_WITH_BG_COLOR) &&
                                mSplashscreenContentDrawer.getField("mTmpAttrs")!!.getField("mWindowBgColor") != 0

                        /**
                         * ??????????????????????????????????????????????????????????????????????????????
                         */
                        if (args(1).boolean()) {
                            val iconScale = mSplashscreenContentDrawer.getField<Int>("mIconSize")!!.toFloat() /
                                    mSplashscreenContentDrawer.getField<Int>("mDefaultIconSize")!!.toFloat()
                            val densityDpi = instance.getField<Context>("mContext")!!.resources.configuration.densityDpi
                            val scaledIconDpi = (0.5f + iconScale * densityDpi * 1.2f).toInt()
                            if (isAtLeastT) {
                                mSplashscreenContentDrawer.getField("mHighResIconProvider")!!.current {
                                    args(0).set(method { name = "getIcon"; param(ActivityInfoClass, IntType, IntType) }
                                        .invoke<Drawable>(instance.getField("mActivityInfo"), densityDpi, scaledIconDpi))
                                    printLog("9. createIconDrawable(): replace the icons processed by the system")
                                }
                            } else{
                                mSplashscreenContentDrawer.getField("mIconProvider")!!.current {
                                    args(0).set(method { name = "getIcon"; param(ActivityInfoClass, IntType) }
                                        .invoke<Drawable>(instance.getField("mActivityInfo"), scaledIconDpi))
                                    printLog("9. createIconDrawable(): replace the icons processed by the system")
                                }
                            }
                        }

                        /**
                         * ?????????????????????
                         * - ??????????????????
                         * - ??????????????????????????????
                         *
                         * ??????????????? createIconDrawable() ???????????? Splash Screen ?????????????????????????????????????????????
                         * ???????????????????????? Palette API ????????????????????????????????????????????????????????????????????????????????? mThemeColor
                         * ????????????????????????????????????
                         */
                        if (prefs.get(DataConst.REMOVE_BG_COLOR).also { if (it) printLog("10. createIconDrawable(): skip set bg color cuz REMOVE_BG_COLOR is on")} ||
                            skipAppWithBgColor.also { if (it) printLog("10. createIconDrawable(): skip set bg color cuz app has been set bg color") })
                            return@beforeHook

                        fun getColor() = if (pkgName in individualBgColorAppMap.keys) {
                            printLog("10. createIconDrawable(): set individual background color, ${individualBgColorAppMap[pkgName]}")
                            Color.parseColor(individualBgColorAppMap[pkgName])
                        } else if (!isInExceptList && (!isDarkMode || ignoreDarkMode))
                            when (bgColorType) {

                                // ???????????????
                                1 -> {
                                    printLog("10. createIconDrawable(): get adaptive background color")
                                    GraphicUtils.getBgColor(
                                        GraphicUtils.drawable2Bitmap(args(0).cast<Drawable>()!!, 100)!!,
                                        when (colorMode) {
                                            1 -> false
                                            2 -> !isDarkMode
                                            else -> true
                                        }
                                    )
                                }

                                // ???????????????
                                2 -> {
                                    printLog("10. createIconDrawable(): get monet background color")
                                    when (colorMode) {
                                        0 -> dynamicLightColorScheme(appContext!!).primaryContainer.toArgb()
                                        1 -> dynamicDarkColorScheme(appContext!!).primaryContainer.toArgb()
                                        else -> if (!isDarkMode)
                                            dynamicLightColorScheme(appContext!!).primaryContainer.toArgb()
                                        else
                                            dynamicDarkColorScheme(appContext!!).primaryContainer.toArgb()
                                    }
                                }
                                else -> { printLog("10. createIconDrawable(): not replace background color"); null }
                            } else { printLog("10. createIconDrawable(): skip set bg color cuz app in except list"); null }

                        if (enableDataCache && bgColorType != 2) { colorData.getOrPut(pkgName) { getColor() } }
                        else { getColor() }?.let {
                            printLog("action: createIconDrawable(): ${if (enableDataCache) "(from cache)" else ""}set background color")
                            instance.setField("mThemeColor", it)
                        }
                    }
                }
            }

        /**
         * ????????????
         *
         * ?????????????????????
         * - ????????????????????????
         * - ???????????????
         * - ??????????????????
         *
         * ????????? com.android.wm.shell.startingsurface.SplashscreenContentDrawer
         *   .$StartingWindowViewBuilder.build() ????????????
         */
        VariousClass(
            "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$HighResIconProvider",
            "com.android.launcher3.icons.IconProvider"
        ).hook {
            injectMember {
                method {
                    name = "getIcon"
                    if (isAtLeastT) param(ActivityInfoClass, IntType, IntType)
                    else param(ActivityInfoClass, IntType)
                }
                afterHook {
                    val enableDataCache = prefs.get(DataConst.ENABLE_DATA_CACHE)
                    val enableReplaceIcon = prefs.get(DataConst.ENABLE_REPLACE_ICON)
                    val shrinkIconType = prefs.get(DataConst.SHRINK_ICON)
                    val iconPackPackageName = prefs.get(DataConst.ICON_PACK_PACKAGE_NAME)
                    val isDrawIconRoundCorner = prefs.get(DataConst.ENABLE_DRAW_ROUND_CORNER)
                    val pkgName = args(0).cast<ActivityInfo>()?.packageName!!
                    val pkgActivity = args(0).cast<ActivityInfo>()?.targetActivity
                    val iconSize = appResources!!.getDimensionPixelSize(
                        "com.android.internal.R\$dimen".toClass().field { name = "starting_surface_icon_size" }.get().int()
                    )

                    if (isExcept(pkgName)) return@afterHook

                    fun getDrawable(): Drawable {
                        /**
                         * ????????????????????????
                         *
                         * ?????? Context.packageManager.getApplicationIcon() ?????????????????????
                         */
                        var drawable = if (enableReplaceIcon || pkgName == "com.android.settings") {
                            when {
                                pkgName == "com.android.contacts" && pkgActivity == "com.android.contacts.activities.PeopleActivity" ->
                                    appContext!!.packageManager.getActivityIcon(
                                        ComponentName(
                                            "com.android.contacts",
                                            "com.android.contacts.activities.TwelveKeyDialer"
                                        )
                                    )
                                pkgName == "com.android.settings" && pkgActivity == "com.android.settings.BackgroundApplicationsManager" ->
                                    appContext!!.packageManager.getApplicationIcon("com.android.settings")
                                else -> pkgName.let {
                                    appContext!!.packageManager.getApplicationIcon(
                                        it
                                    )
                                }
                            }
                        } else {
                            result<Drawable>()!!
                        }
                        printLog("6. getIcon(): ${if (enableReplaceIcon) "" else "Not"} replace way of getting icon")

                        // ???????????????
                        if (iconPackPackageName != "None") {
                            when {
                                pkgName == "com.android.contacts" && pkgActivity == "com.android.contacts.activities.PeopleActivity" ->
                                    iconPackManager.getIconByComponentName("ComponentInfo{com.android.contacts/com.android.contacts.activities.TwelveKeyDialer}")
                                else -> iconPackManager.getIconByPackageName(pkgName)
                            }?.let { drawable = it }
                        }
                        printLog("7. getIcon(): ${if (iconPackPackageName != "None") "" else "Not"} use Icon Pack")


                        /**
                         * ??????????????????
                         * ????????????
                         */
                        GraphicUtils.roundBitmapByShader(
                            drawable.let { GraphicUtils.drawable2Bitmap(it, iconSize) },
                            if (isDrawIconRoundCorner) RoundDegree.RoundCorner else RoundDegree.NotDrawRoundCorner,
                            when (shrinkIconType) {
                                0 -> 0               // ???????????????
                                1 -> iconSize / 4    // ?????????????????????????????????
                                else -> 5000         // ??????????????????
                            }
                        )?.let { drawable = BitmapDrawable(appResources, it) }
                        printLog("8. getIcon(): ${if (isDrawIconRoundCorner) "" else "Not"} draw round corner; shrink icon type is $shrinkIconType")

                        return drawable
                    }

                    printLog("action: getIcon(): ${if (enableDataCache) "(from cache)" else ""}set drawable icon")
                    result = if (enableDataCache) iconData.getOrPut(pkgName) { getDrawable() }
                    else getDrawable()
                }
            }
        }

        /**
         * ??????????????????
         *
         * ?????????????????? framework.jar ???
         *
         * ????????? com.android.wm.shell.startingsurface.SplashscreenContentDrawer
         *   .$StartingWindowViewBuilder.fillViewWithIcon() ????????????
         */

        val ignoreDarkModeHook: HookParam.() -> Unit = {
            if (prefs.get(DataConst.IGNORE_DARK_MODE)) {
                resultFalse()
                printLog("11. isStaringWindowUnderNightMode(): ignore dark mode")
            }
        }
        findClass("android.window.SplashScreenView\$Builder").hook {
            injectMember {
                method {
                    name = "isStaringWindowUnderNightMode"
                    emptyParam()
                }
                beforeHook(ignoreDarkModeHook)
            }.ignoredNoSuchMemberFailure()
        }
        findClass("android.view.ForceDarkHelperStubImpl").hook {
            injectMember {
                method {
                    name = "updateForceDarkSplashScreen"
                    paramCount(3)
                }
                beforeHook(ignoreDarkModeHook)
            }.ignoredNoSuchMemberFailure()
        }


        // ????????????????????????
        findClass("com.android.wm.shell.startingsurface.StartingWindowController").hook {
            injectMember {
                method { name = "removeStartingWindow" }
                replaceUnit {
                    when (currentPackageName) {
                        null -> { callOriginal(); return@replaceUnit }
                        // ????????????????????????????????????
                        in prefs.get(DataConst.MIN_DURATION_LIST) -> {
                            val configMap = getMapPrefs(DataConst.MIN_DURATION_CONFIG_MAP)
                            try {
                                configMap[currentPackageName].toString().toLong().let { duration ->
                                    if (duration != 0L) {
                                        printLog("12. removeStartingWindow(): remove splash screen of $currentPackageName after $duration ms")
                                        MainScope().launch {
                                            delay(duration)
                                            callOriginal()
                                        }
                                    } else callOriginal()
                                }
                            } catch (_: NumberFormatException) {
                                callOriginal()
                                printLog("12. removeStartingWindow(): $currentPackageName: a NumberFormatException is threw")
                            }
                        }
                        // ?????????
                        else -> prefs.get(DataConst.MIN_DURATION).let { duration ->
                            if (duration != 0) {
                                printLog("12. removeStartingWindow(): remove splash screen of $currentPackageName after $duration ms (default value)")
                                MainScope().launch {
                                    delay(duration.toLong())
                                    callOriginal()
                                }
                            } else callOriginal()
                        }
                    }
                    currentPackageName = null
                }
            }
        }

        /**
         * ??????????????????
         *
         * ?????????????????? miui-framework.jar ???
         *
         * ????????? com.android.wm.shell.startingsurface.SplashscreenContentDrawer
         *   .$StartingWindowViewBuilder.fillViewWithIcon() ????????????
         *
         * ??????????????? fillViewWithIcon() ?????? if ?????????????????????????????????????????? MIUI ??????
         */
        findClass("android.app.TaskSnapshotHelperImpl").hook {
            injectMember {
                method {
                    name = "isMiuiHome"
                    param(StringType)
                }
                beforeHook {
                    if (prefs.get(DataConst.REMOVE_BG_DRAWABLE)) {
                        resultFalse()
                        printLog(
                            "12. isMiuiHome(): set isMiuiHome() false"
                        )
                    }
                }
            }.ignoredNoSuchMemberFailure()
        }

        findClass("com.android.wm.shell.startingsurface.OplusShellStartingWindowManager").hook {
            injectMember {
                method { name = "setContentViewBackground" }
                beforeHook {
                    printLog("ColorOS: setContentViewBackground(): intercept!!")
                    result = null
                }
            }
        }.ignoredHookClassNotFoundFailure()
    }
}
