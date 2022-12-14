package com.gswxxn.restoresplashscreen.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import cn.fkj233.ui.dialog.MIUIDialog
import com.gswxxn.restoresplashscreen.R
import com.gswxxn.restoresplashscreen.data.ConstValue
import com.gswxxn.restoresplashscreen.databinding.ActivityConfigAppsBinding
import com.gswxxn.restoresplashscreen.databinding.AdapterConfigBinding
import com.gswxxn.restoresplashscreen.ui.`interface`.IConfigApps
import com.gswxxn.restoresplashscreen.ui.configapps.*
import com.gswxxn.restoresplashscreen.utils.AppInfoHelper
import com.gswxxn.restoresplashscreen.utils.BlockMIUIHelper.addBlockMIUIView
import com.gswxxn.restoresplashscreen.utils.CommonUtils.notEqualsTo
import com.gswxxn.restoresplashscreen.utils.CommonUtils.toMap
import com.gswxxn.restoresplashscreen.utils.CommonUtils.toSet
import com.gswxxn.restoresplashscreen.utils.CommonUtils.toast
import com.gswxxn.restoresplashscreen.utils.YukiHelper.sendToHost
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import kotlinx.coroutines.*

class ConfigAppsActivity : BaseActivity<ActivityConfigAppsBinding>(), CoroutineScope by MainScope() {
    companion object {
        var isDarkMode: Boolean = false
    }

    lateinit var appInfo: AppInfoHelper
    lateinit var configMap: MutableMap<String, String>
    lateinit var checkedList : MutableSet<String>
    lateinit var appInfoFilter: List<AppInfoHelper.MyAppInfo>
    lateinit var instance: IConfigApps
    var onRefreshList: (() -> Unit)? = null

    override fun onCreate() {
        window.statusBarColor = getColor(R.color.colorThemeBackground)
        isDarkMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        instance = when (intent.getIntExtra(ConstValue.EXTRA_MESSAGE, 0)) {
            ConstValue.CUSTOM_SCOPE -> CustomScope
            ConstValue.DEFAULT_STYLE -> DefaultStyle
            ConstValue.BACKGROUND_EXCEPT -> BackgroundExcept
            ConstValue.BRANDING_IMAGE -> BrandingImage
            ConstValue.FORCE_SHOW_SPLASH_SCREEN -> ForceShowSplashScreen
            ConstValue.MIN_DURATION -> MinDuration
            ConstValue.BACKGROUND_INDIVIDUALLY_CONFIG -> BGColorIndividualConfig
            else -> { object : IConfigApps {
                override val titleID: Int get() = R.string.unavailable
                override val submitSet: Boolean
                    get() = false
            } }
        }

        // ???????????????????????? Set
        checkedList = modulePrefs.get(instance.checkedListPrefs).toMutableSet()
        // ??????????????????
        configMap = modulePrefs.get(instance.configMapPrefs).toMap()
        // AppInfoHelper ??????
        appInfo = AppInfoHelper(this, checkedList, configMap)
        // ?????????????????????
        appInfoFilter =  listOf()

        fun searchEvent() {
            val content = binding.searchEditText.text.toString()
            appInfoFilter = if (content.isBlank()) {
                appInfo.getAppInfoList()
            } else {
                appInfo.getAppInfoList().filter { it.appName.contains(content) or it.packageName.contains(content) }
            }
            onRefreshList?.invoke()
        }

        launch {
            appInfoFilter = withContext(Dispatchers.Default) { appInfo.getAppInfoList() }

            showView(false, binding.configListLoadingView)
            showView(true, binding.configListView)

            // ???????????????????????????
            binding.searchEditText.addTextChangedListener(object : TextWatcher {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchEvent()
                }
                override fun afterTextChanged(s: Editable?) {}
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            })
            searchEvent()
        }

        //????????????????????????
        binding.titleBackIcon.setOnClickListener { onBackPressed() }

        // ????????????
        binding.appListTitle.text = getString(instance.titleID)
        binding.subSettingHint.text = getString(instance.subSettingHint)

        // ????????????????????????
        binding.configTitleFilter.setOnClickListener {
            binding.searchEditText.apply {
                visibility = View.VISIBLE
                requestFocus()
            }
        }

        // ?????????????????????
        binding.searchEditText.apply {
            // ????????????
            setOnFocusChangeListener { v, hasFocus ->
                val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (hasFocus) {
                    showView(false, binding.appListTitle, binding.configDescription, binding.configTitleFilter, binding.overallSettings)
                    // ???????????????
                    imm.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_FORCED)
                }else {
                    // ???????????????
                    imm.hideSoftInputFromWindow(this.windowToken, 0)
                }
            }
        }

        // ????????????
        binding.overallSettings.addBlockMIUIView(this@ConfigAppsActivity, itemData = instance.blockMIUIView(this@ConfigAppsActivity))

        // ??????
        binding.configListView.apply {
            adapter = object : BaseAdapter() {
                override fun getCount() = appInfoFilter.size

                override fun getItem(position: Int) = appInfoFilter[position]

                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    var cView = convertView
                    val holder: AdapterConfigBinding
                    if (convertView == null) {
                        holder = AdapterConfigBinding.inflate(LayoutInflater.from(context))
                        cView =holder.root
                        cView.tag = holder
                    }else {
                        holder = cView?.tag as AdapterConfigBinding
                    }
                    getItem(position).also { item ->
                        // ????????????
                        holder.adpAppIcon.setImageDrawable(item.icon)
                        // ???????????????
                        holder.adpAppName.text = item.appName
                        // ????????????
                        holder.adpAppPkgName.text = item.packageName
                        // ?????? TextView
                        holder.adpAppTextView.apply(instance.adpTextView(this@ConfigAppsActivity, holder, item))
                        // ???????????????
                        holder.adpAppCheckBox.apply(instance.adpCheckBox(this@ConfigAppsActivity, holder, item))
                        // ?????? LinearLayout ????????????
                        holder.adapterLayout.setOnClickListener(instance.adpLinearLayout(this@ConfigAppsActivity, holder, item))
                    }
                    return cView
                }
            }.apply{ onRefreshList = { notifyDataSetChanged() } }
        }

        // ????????????????????????
        binding.configSaveButton.setOnClickListener {
            if (instance.submitSet)
                modulePrefs.put(instance.checkedListPrefs, checkedList)
            if (instance.submitMap)
                modulePrefs.put(instance.configMapPrefs, configMap.toSet())
            sendToHost()
            toast(getString(R.string.save_successful))
            finish()
        }

        // ???????????????
        binding.moreOptions.apply(instance.moreOptions(this))
    }

    override fun onBackPressed() {
        if (binding.searchEditText.isFocused){
            binding.searchEditText.apply {
                clearFocus()
                visibility = View.GONE
                text = null
            }
            showView(true, binding.appListTitle, binding.configDescription, binding.configTitleFilter, binding.overallSettings)
        } else if ((instance.submitSet && modulePrefs.get(instance.checkedListPrefs) notEqualsTo checkedList) ||
            (instance.submitMap && modulePrefs.get(instance.configMapPrefs) notEqualsTo configMap.toSet())) {
            MIUIDialog(this) {
                setTitle(getString(R.string.not_saved_title))
                setMessage(getString(R.string.not_saved_hint))
                setRButton(getString(R.string.button_okay)) { this@ConfigAppsActivity.cancel(); super.onBackPressed() }
                setLButton(getString(R.string.button_cancel)) { dismiss() }
            }.show()
        } else {
            cancel()
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        instance.onActivityResult(this, requestCode, resultCode, data)
}