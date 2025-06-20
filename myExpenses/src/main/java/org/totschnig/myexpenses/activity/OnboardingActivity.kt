package org.totschnig.myexpenses.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.evernote.android.state.State
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.databinding.OnboardingBinding
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment
import org.totschnig.myexpenses.dialog.RestoreFromCloudDialogFragment
import org.totschnig.myexpenses.fragment.OnBoardingPrivacyFragment
import org.totschnig.myexpenses.fragment.OnboardingDataFragment
import org.totschnig.myexpenses.fragment.OnboardingUiFragment
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.sync.json.AccountMetaData
import org.totschnig.myexpenses.ui.FragmentPagerAdapter
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.distrib.DistributionHelper.versionNumber
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.RestoreViewModel.Companion.KEY_BACKUP_FROM_SYNC
import org.totschnig.myexpenses.viewmodel.RestoreViewModel.Companion.KEY_PASSWORD
import org.totschnig.myexpenses.viewmodel.SyncViewModel.SyncAccountData

class OnboardingActivity : SyncBackendSetupActivity() {
    private lateinit var binding: OnboardingBinding
    private lateinit var pagerAdapter: MyPagerAdapter

    private val startBanking =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                start()
            }
        }

    @State
    var accountName: String? = null

    override val drawToTopEdge = true

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            try {
                prefHandler.setDefaultValues(this)
            } catch (e: Exception) {
                //According to report, setDefaultValues fails in some scenario
                //where there is a value of the wrong type already present
                //java.lang.ClassCastException: java.lang.Boolean cannot be cast to java.lang.String
                //maybe when data is restored via Play Store app backup
                CrashHandler.report(e)
            }
        }
        super.onCreate(savedInstanceState)
        binding = OnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pagerAdapter = MyPagerAdapter(supportFragmentManager)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 2
        ViewCompat.setOnApplyWindowInsetsListener(binding.pageIndicatorView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = bars.top
            }
            insets
        }
    }

    override fun onCreateOptionsMenu(menu: Menu) = false //skip help

    fun navigateNext() {
        val currentItem = binding.viewPager.currentItem
        binding.viewPager.setCurrentItem(currentItem + 1, true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val currentItem = binding.viewPager.currentItem
        if (currentItem > 0) {
            binding.viewPager.currentItem = currentItem - 1
            return
        }
        super.onBackPressed()
    }

    private val dataFragment: OnboardingDataFragment?
        get() = getFragmentAtPosition(2) as? OnboardingDataFragment

    private val privacyFragment: OnBoardingPrivacyFragment?
        get() = getFragmentAtPosition(1) as? OnBoardingPrivacyFragment

    private fun getFragmentAtPosition(pos: Int) =
        supportFragmentManager.findFragmentByTag(pagerAdapter.getFragmentName(pos))

    fun start() {
        prefHandler.putInt(PrefKey.CURRENT_VERSION, versionNumber)
        prefHandler.putInt(PrefKey.FIRST_INSTALL_VERSION, versionNumber)
        val intent = Intent(this, MyExpenses::class.java)
        startActivity(intent)
        finish()
    }

    override val createAccountTaskShouldReturnBackups = true

    override val createAccountTaskShouldQueryLocalAccounts = false

    override fun onReceiveSyncAccountData(data: SyncAccountData) {
        lifecycleScope.launchWhenResumed {
            dataFragment?.setupMenu()
            accountName = data.accountName
            if (data.backups.isNotEmpty() || data.remoteAccounts.isNotEmpty()) {
                if (checkForDuplicateUuids(data.remoteAccounts)) {
                    showSnackBar("Found accounts with duplicate uuids")
                } else {
                    RestoreFromCloudDialogFragment.newInstance(data.backups, data.remoteAccounts)
                        .show(supportFragmentManager, FRAGMENT_TAG_RESTORE)
                }
            } else {
                showSnackBar("Neither backups nor sync accounts found")
            }
        }
    }

    override fun onPostRestoreTask(result: Result<Unit>) {
        super.onPostRestoreTask(result)
        result.onSuccess {
            restartAfterRestore()
        }
    }

    fun setupFromBackup(backup: String?, password: String?) {
        doRestore(Bundle(4).apply {
            putString(DatabaseConstants.KEY_SYNC_ACCOUNT_NAME, accountName)
            putString(KEY_BACKUP_FROM_SYNC, backup)
            putString(KEY_PASSWORD, password)
        })
    }

    fun setupFromSyncAccounts(syncAccounts: List<AccountMetaData>) {
        doWithEncryptionCheck {
            showSnackBarIndefinite(R.string.progress_dialog_fetching_data_from_sync_backend)
            syncViewModel.setupFromSyncAccounts(syncAccounts.map { it.uuid() }, accountName!!)
                .observe(this) { result ->
                    dismissSnackBar()
                    result.onSuccess {
                        start()
                    }.onFailure {
                        showDismissibleSnackBar(it.safeMessage)
                    }
                }
        }
    }

    private inner class MyPagerAdapter(fm: FragmentManager?) :
        FragmentPagerAdapter(fm) {
        fun getFragmentName(currentPosition: Int): String {
            return makeFragmentName(binding.viewPager.id, getItemId(currentPosition))
        }

        override fun getItem(pos: Int) = when (pos) {
            0 -> OnboardingUiFragment.newInstance()
            1 -> OnBoardingPrivacyFragment.newInstance()
            else -> OnboardingDataFragment.newInstance()
        }

        override fun getCount() = 3
    }

    override fun onNegative(args: Bundle) {
        if (args.getInt(ConfirmationDialogFragment.KEY_COMMAND_NEGATIVE) == R.id.ENCRYPT_CANCEL_COMMAND) {
            prefHandler.putBoolean(PrefKey.ENCRYPT_DATABASE, false)
            privacyFragment?.setupMenu()
        }
    }

    override fun onNeutral(args: Bundle) {
        if (args.getInt(ConfirmationDialogFragment.KEY_COMMAND_NEUTRAL) == R.id.ENCRYPT_LEARN_MORE_COMMAND) {
            startActionView("https://faq.myexpenses.mobi/data-encryption")
        }
    }

    override fun startBanking() {
        startBanking.launch(Intent(this, bankingFeature.bankingActivityClass))
    }

    override val snackBarContainerId: Int
        get() {
            return binding.viewPager.id
        }
}